package zio
package interop

import cats.Eq
import cats.effect.{ ConcurrentEffect, ContextShift }
import cats.effect.laws.ConcurrentEffectLaws
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests, Parameters }
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import cats.laws.discipline.MonadTests
import cats.laws.discipline.{ AlternativeTests, BifunctorTests, MonadErrorTests, ParallelTests, SemigroupKTests }
import org.scalacheck.{ Arbitrary, Cogen }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.{ BeforeAndAfterAll, Matchers }
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.internal.PlatformLive
import zio.interop.catz._
import zio.random.Random
import zio.system.System

object IOConcurrentEffectTests {
  def apply()(implicit ce: ConcurrentEffect[Task], cs: ContextShift[Task]): ConcurrentEffectTests[Task] =
    new ConcurrentEffectTests[Task] {
      def laws =
        new ConcurrentEffectLaws[Task] {
          override val F: ConcurrentEffect[Task]        = ce
          override val contextShift: ContextShift[Task] = cs
        }
    }
}

class catzSpec
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with Checkers
    with Discipline
    with TestInstances
    with GenIO {

  type Env = Clock with Console with System with Random with Blocking

  implicit val rts: Runtime[Env] = new DefaultRuntime {
    override val Platform = PlatformLive.makeDefault().withReportFailure(_ => ())
  }

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit = {
    val context = TestContext()
    val ruleSet = f(context)

    checkAll(name, ruleSet)
  }

  override implicit def eqIO[A](implicit A: Eq[A], ec: TestContext): Eq[cats.effect.IO[A]] =
    new Eq[cats.effect.IO[A]] {
      import cats.syntax.apply._
      import scala.concurrent.duration._

      def eqv(x: cats.effect.IO[A], y: cats.effect.IO[A]): Boolean = {
        val duration = 20.seconds
        val leftM    = x.attempt.unsafeRunTimed(duration)
        val rightM   = y.attempt.unsafeRunTimed(duration)

        val res = (leftM, rightM).mapN {
          (_, _) match {
            case (Right(l), Right(r)) => A.eqv(l, r)
            case (Left(l), Left(r))   => l == r
            case _                    => false
          }
        }

        res.getOrElse {
          println(s"One of actions timed out, results are: leftM = $leftM, rightM = $rightM")
          false
        }
      }
    }

  // TODO: reintroduce repeated ConcurrentTests as they're removed due to the hanging CI builds (see https://github.com/scalaz/scalaz-zio/pull/482)
  checkAllAsync(s"ConcurrentEffect[Task]", implicit tctx => IOConcurrentEffectTests().concurrentEffect[Int, Int, Int])
  checkAllAsync("Effect[Task]", implicit tctx => EffectTests[Task].effect[Int, Int, Int])
  checkAllAsync("Concurrent[Task]", (_) => ConcurrentTests[Task].concurrent[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, ?]]", (_) => MonadErrorTests[IO[Int, ?], Int].monadError[Int, Int, Int])
  checkAllAsync("Alternative[IO[Int, ?]]", (_) => AlternativeTests[IO[Int, ?]].alternative[Int, Int, Int])
  checkAllAsync(
    "Alternative[IO[Option[Unit], ?]]",
    (_) => AlternativeTests[IO[Option[Unit], ?]].alternative[Int, Int, Int]
  )
  checkAllAsync("SemigroupK[Task]", (_) => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", (_) => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task, Task.Par]", (_) => ParallelTests[Task, Util.Par].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", (_) => MonadTests[UIO].apply[Int, Int, Int])

  checkAllAsync("Monad[UIO]", (_) => ExtraMonadTests[UIO].monadExtras[Int])

  // ZManaged Tests
  checkAllAsync("Monad[ZManaged]", (_) => MonadTests[ZManaged[Any, Throwable, ?]].apply[Int, Int, Int])
  checkAllAsync("Monad[ZManaged]", (_) => ExtraMonadTests[ZManaged[Any, Throwable, ?]].monadExtras[Int])
  checkAllAsync("SemigroupK[ZManaged]", (_) => SemigroupKTests[ZManaged[Any, Throwable, ?]].semigroupK[Int])
  checkAllAsync("MonadError[ZManaged]", (_) => MonadErrorTests[ZManaged[Any, Int, ?], Int].monadError[Int, Int, Int])

  object summoningInstancesTest {
    import cats._, cats.effect._
    Concurrent[TaskR[String, ?]]
    Async[TaskR[String, ?]]
    LiftIO[TaskR[String, ?]]
    Sync[TaskR[String, ?]]
    MonadError[TaskR[String, ?], Throwable]
    Monad[TaskR[String, ?]]
    Applicative[TaskR[String, ?]]
    Functor[TaskR[String, ?]]
    Parallel[TaskR[String, ?], ParIO[String, Throwable, ?]]
    SemigroupK[TaskR[String, ?]]
    Apply[UIO]
    LiftIO[ZManaged[String, Throwable, ?]]
    MonadError[ZManaged[String, Throwable, ?], Throwable]
    Monad[ZManaged[String, Throwable, ?]]
    Applicative[ZManaged[String, Throwable, ?]]
    Functor[ZManaged[String, Throwable, ?]]
    SemigroupK[ZManaged[String, Throwable, ?]]

    def concurrentEffect[R: Runtime] = ConcurrentEffect[TaskR[R, ?]]
    def effect[R: Runtime]           = Effect[TaskR[R, ?]]
  }

  object summoningRuntimeInstancesTest {
    import cats.effect.{ Clock => CatzClock, _ }
    import zio.interop.catz.implicits._

    ContextShift[Task]
    ContextShift[TaskR[String, ?]]
    CatzClock[Task]
    Timer[Task]

    ContextShift[UIO[?]]
    CatzClock[UIO[?]]
    Timer[UIO[?]]
  }

  implicit def catsEQ[E, A: Eq]: Eq[IO[E, A]] =
    new Eq[IO[E, A]] {
      import zio.duration._

      def eqv(io1: IO[E, A], io2: IO[E, A]): Boolean = {
        val v1  = rts.unsafeRunSync(io1.timeoutFail("Test timed out")(20.seconds))
        val v2  = rts.unsafeRunSync(io2.timeoutFail("Test timed out")(20.seconds))
        val res = v1 === v2
        if (!res) {
          println(s"Mismatch: ${v1.fold(_.prettyPrint, _.toString)} != ${v2.fold(_.prettyPrint, _.toString)}")
        }
        res
      }
    }

  implicit def catsParEQ[E: Eq, A: Eq]: Eq[ParIO[Any, E, A]] =
    new Eq[ParIO[Any, E, A]] {
      def eqv(io1: ParIO[Any, E, A], io2: ParIO[Any, E, A]): Boolean =
        rts.unsafeRun(Par.unwrap(io1).either) === rts.unsafeRun(Par.unwrap(io2).either)
    }

  implicit def catsZManagedEQ[E: Eq, A: Eq]: Eq[ZManaged[Any, E, A]] =
    new Eq[ZManaged[Any, E, A]] {
      def eqv(io1: ZManaged[Any, E, A], io2: ZManaged[Any, E, A]): Boolean =
        rts.unsafeRun(io1.reserve.flatMap(_.acquire).either) === rts.unsafeRun(io2.reserve.flatMap(_.acquire).either)
    }

  implicit def params: Parameters =
    Parameters.default.copy(allowNonTerminationLaws = false)

  implicit def zioArbitrary[E, A: Arbitrary: Cogen, R: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(genSuccess[E, A])

  implicit def ioArbitrary[E, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(genSuccess[E, A])

  implicit def ioParArbitrary[E, A: Arbitrary: Cogen, R <: Any]: Arbitrary[ParIO[R, E, A]] =
    Arbitrary(genSuccess[E, A].map(Par.apply))

  implicit def zManagedArbitrary[E, A: Arbitrary: Cogen, R <: Any]: Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(genSuccess[E, A].map(ZManaged.fromEffect(_)))
}
