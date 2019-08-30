package zio.interop

import java.util.concurrent.atomic.AtomicReference

import cats.Eq
import cats.effect.{ Bracket, Resource, SyncIO }
import cats.implicits._
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import zio.internal.{ Executor, PlatformLive }
import zio.interop.catz.taskEffectInstance
import zio.{ Cause, IO, Runtime, UIO, ZIO, ZManaged }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

private[interop] trait catzSpecBase
    extends AnyFunSuite
    with GeneratorDrivenPropertyChecks
    with Discipline
//    with TestInstances
    with GenIOInteropCats
    with catzSpecBaseLowPriority {

  implicit def rts(implicit tc: TestContext0): Runtime[Any] =
    Runtime(
      r = (),
      platform = PlatformLive
        .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(tc))
        .withReportFailure(_ => ())
    )

  implicit def zioEqCause[E]: Eq[Cause[E]] = zioEqCause0.asInstanceOf[Eq[Cause[E]]]
  private val zioEqCause0: Eq[Cause[Any]]  = Eq.fromUniversalEquals

  val counter = new AtomicReference(0)

  /**
   * Defines equality for `Future` references that can
   * get interpreted by means of a [[TestContext0]].
   */
  implicit def eqFuture[A](implicit A: Eq[A], ec: TestContext0): Eq[Future[A]] =
    new Eq[Future[A]] {
      def eqv(x: Future[A], y: Future[A]): Boolean = {
        // Executes the whole pending queue of runnables
        ec.tick()
//        while (ec.tickOne()) ec.tickOne()
//        println(ec.state.tasks)
//        while (ec.tickOne()) {
//          ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//          ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//          ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//          ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//          ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//        }
//        ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//        ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//        ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))
//        ec.tick(concurrent.duration.Duration.fromNanos(1000000000000L))

        //
        //        (Await.result(x, FiniteDuration(2, TimeUnit.SECONDS)), Await.result(y, FiniteDuration(2, TimeUnit.SECONDS))) match {
        //          case (a, b) => A.eqv(a, b)
        //        }

        val res = x.value match {
          case None =>
            y.value match {
              case None =>
                if (counter.updateAndGet(_ + 1) > 31) {
                  java.lang.System.out.println(s"More than 31 non-terminating tasks")
                  false
                } else true
              case right =>
                java.lang.System.out.println(s"Tick mismatch 1 left=None right=$right")
                false
            }
          case left @ Some(Success(a)) =>
            y.value match {
              case Some(Success(b)) =>
                val res = A.eqv(a, b)
                if (!res) java.lang.System.out.println(s"Result mismatch 2 $a $b")
                res
              case Some(Failure(_)) =>
                java.lang.System.out.println("Success mismatch 2")
                false
              case right =>
                java.lang.System.out.println(s"Tick mismatch 2 left=$left right=$right")
                false
            }
          case left @ Some(Failure(ex)) =>
            y.value match {
              case Some(Failure(ey)) =>
                val res = eqThrowable.eqv(ex, ey)
                if (!res) java.lang.System.out.println(s"Result mismatch 3 $ex $ey")
                res
              case Some(Success(_)) =>
                java.lang.System.out.println("Success mismatch 3")
                false
              case right =>
                java.lang.System.out.println(s"Tick mismatch 3 left=$left right=$right")
                false
            }
        }

        if (!res) java.lang.System.out.println("Mismatch")
        res
      }
    }

  implicit def zioEqIO[E: Eq, A: Eq](implicit tc: TestContext0, rts: Runtime[Any]): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def zioEqUIO[A: Eq](implicit tc: TestContext0, rts: Runtime[Any]): Eq[UIO[A]] =
    Eq.by(uio => rts.unsafeRunToFuture(uio.sandbox.either))

  implicit def zioEqParIO[E: Eq, A: Eq](implicit tc: TestContext0, rts: Runtime[Any]): Eq[ParIO[Any, E, A]] =
    Eq.by(Par.unwrap(_))

  implicit def zioEqZManaged[E: Eq, A: Eq](implicit tc: TestContext0, rts: Runtime[Any]): Eq[ZManaged[Any, E, A]] =
    Eq.by(_.reserve.flatMap(_.acquire).either)

  implicit def zioArbitrary[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[R => IO[E, A]].map(ZIO.environment[R].flatMap(_)))

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))

  implicit def ioParArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ParIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(Par.apply))

  implicit def zManagedArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(ZManaged.fromEffect(_)))

  def checkAllAsync(name: String, f: TestContext0 => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext0()))

  /**
   * Defines equality for `IO` references that can
   * get interpreted by means of a [[TestContext]].
   */
  implicit def eqIO[A](implicit A: Eq[A], ec: TestContext0): Eq[cats.effect.IO[A]] =
    new Eq[cats.effect.IO[A]] {
      def eqv(x: cats.effect.IO[A], y: cats.effect.IO[A]): Boolean =
        eqFuture[A].eqv(x.unsafeToFuture(), y.unsafeToFuture())
    }

  /**
   * Defines equality for `IO.Par` references that can
   * get interpreted by means of a [[TestContext]].
   */
  implicit def eqIOPar[A](implicit A: Eq[A], ec: TestContext0): Eq[cats.effect.IO.Par[A]] =
    new Eq[cats.effect.IO.Par[A]] {
      import cats.effect.IO.Par.unwrap
      def eqv(x: cats.effect.IO.Par[A], y: cats.effect.IO.Par[A]): Boolean =
        eqFuture[A].eqv(unwrap(x).unsafeToFuture(), unwrap(y).unsafeToFuture())
    }

  implicit val eqThrowable: Eq[Throwable] =
    new Eq[Throwable] {
      def eqv(x: Throwable, y: Throwable): Boolean =
        // All exceptions are non-terminating and given exceptions
        // aren't values (being mutable, they implement reference
        // equality), then we can't really test them reliably,
        // especially due to race conditions or outside logic
        // that wraps them (e.g. ExecutionException)
        (x ne null) == (y ne null)
    }

  /**
   * Defines equality for a `Resource`.  Two resources are deemed
   * equivalent if they allocate an equivalent resource.  Cleanup,
   * which is run purely for effect, is not considered.
   */
  implicit def eqResource[F[_], A](implicit E: Eq[F[A]], F: Bracket[F, Throwable]): Eq[Resource[F, A]] =
    new Eq[Resource[F, A]] {
      def eqv(x: Resource[F, A], y: Resource[F, A]): Boolean =
        E.eqv(x.use(F.pure), y.use(F.pure))
    }

  /** Defines equality for `SyncIO` references. */
  implicit def eqSyncIO[A](implicit A: Eq[A]): Eq[SyncIO[A]] =
    new Eq[SyncIO[A]] {
      def eqv(x: SyncIO[A], y: SyncIO[A]): Boolean = {
        val eqETA = cats.kernel.instances.either.catsStdEqForEither(eqThrowable, A)
        eqETA.eqv(x.attempt.unsafeRunSync(), y.attempt.unsafeRunSync())
      }
    }

}

private[interop] trait catzSpecBaseLowPriority { this: catzSpecBase =>

  implicit def zioEq[R: Arbitrary, E, A: Eq](implicit tc: TestContext0, rts: Runtime[Any]): Eq[ZIO[R, E, A]] = {
    def run(r: R, zio: ZIO[R, E, A]) = taskEffectInstance.toIO(zio.provide(r).sandbox.either)
    Eq.instance((io1, io2) => Arbitrary.arbitrary[R].sample.fold(false)(r => catsSyntaxEq(run(r, io1)) eqv run(r, io2)))
  }

}
