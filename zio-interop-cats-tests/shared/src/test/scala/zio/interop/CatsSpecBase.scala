package zio.interop

import cats.effect.testkit.TestInstances
import cats.effect.kernel.Outcome
import cats.effect.IO as CIO
import cats.syntax.all.*
import cats.{ Eq, Order }
import org.scalacheck.{ Arbitrary, Cogen, Gen, Prop }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import zio.*
import zio.managed.*

import java.time.temporal.ChronoUnit
import java.time.{ Instant, LocalDateTime, OffsetDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.{ FiniteDuration, TimeUnit }
import scala.language.implicitConversions

private[zio] trait CatsSpecBase
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Configuration
    with TestInstances
    with CatsSpecBaseLowPriority {

  def checkAllAsync(name: String, f: Ticker => Laws#RuleSet): Unit =
    checkAll(name, f(Ticker()))

  val environment: ZEnvironment[Any] =
    ZEnvironment(())

  def testClock(implicit ticker: Ticker) = new Clock {

    def instant(implicit trace: Trace): UIO[Instant]                     =
      ???
    def localDateTime(implicit trace: Trace): UIO[LocalDateTime]         =
      ???
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(ticker.ctx.now().toUnit(unit).toLong)

    def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
      ZIO.succeed(unit.between(Instant.EPOCH, Instant.ofEpochMilli(ticker.ctx.now().toMillis)))

    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))

    def javaClock(implicit trace: zio.Trace): zio.UIO[java.time.Clock] =
      ???

    def nanoTime(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(ticker.ctx.now().toNanos)

    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] = duration.asScala match {
      case finite: FiniteDuration =>
        ZIO.asyncInterrupt { cb =>
          val cancel = ticker.ctx.schedule(finite, () => cb(ZIO.unit))
          Left(ZIO.succeed(cancel()))
        }
      case infinite: Infinite     =>
        ZIO.dieMessage(s"Unexpected infinite duration $infinite passed to Ticker")
    }

    def scheduler(implicit trace: Trace): UIO[Scheduler] =
      ???
  }

  def unsafeRun[E, A](io: IO[E, A])(implicit ticker: Ticker): Exit[E, Option[A]] =
    try {
      var exit: Exit[E, Option[A]] = Exit.succeed(Option.empty[A])
      Unsafe.unsafe { implicit u =>
        val fiber = runtime.unsafe.fork[E, Option[A]](io.asSome)
        fiber.unsafe.addObserver(exit = _)
      }
      ticker.ctx.tickAll(FiniteDuration(1, TimeUnit.SECONDS))
      exit
    } catch {
      case error: Throwable =>
        error.printStackTrace()
        throw error
    }

  implicit def runtime(implicit ticker: Ticker): Runtime[Any] = {
    val executor         = Executor.fromExecutionContext(ticker.ctx)
    val blockingExecutor = Executor.fromExecutionContext(ticker.ctx)
    val fiberId          = Unsafe.unsafe(implicit u => FiberId.make(Trace.empty))
    val fiberRefs        = FiberRefs(
      Map(
        FiberRef.overrideExecutor        -> ::(fiberId -> Some(executor), Nil),
        FiberRef.currentBlockingExecutor -> ::(fiberId -> blockingExecutor, Nil)
      )
    )
    val runtimeFlags     = RuntimeFlags.default
    Runtime(ZEnvironment.empty, fiberRefs, runtimeFlags)
  }

  implicit val arbitraryAny: Arbitrary[Any] =
    Arbitrary(Gen.const(()))

  implicit def arbitraryChunk[A: Arbitrary]: Arbitrary[Chunk[A]] =
    Arbitrary(Gen.listOf(Arbitrary.arbitrary[A]).map(Chunk.fromIterable))

  implicit val cogenForAny: Cogen[Any] =
    Cogen(_.hashCode.toLong)

  implicit val arbitraryEnvironment: Arbitrary[ZEnvironment[Any]] =
    Arbitrary(Gen.const(environment))

  implicit val eqForNothing: Eq[Nothing] =
    Eq.allEqual

  implicit val eqForExecutionContext: Eq[ExecutionContext] =
    Eq.allEqual

  implicit val eqForCauseOfNothing: Eq[Cause[Nothing]] =
    (x, y) => (x.isInterrupted && y.isInterrupted) || x == y

  implicit def eqForExitOfNothing[A: Eq]: Eq[Exit[Nothing, A]] = {
    case (Exit.Success(x), Exit.Success(y)) => x eqv y
    case (Exit.Failure(x), Exit.Failure(y)) => x eqv y
    case _                                  => false
  }

  implicit def eqForUIO[A: Eq](implicit ticker: Ticker): Eq[UIO[A]] = { (uio1, uio2) =>
    val exit1 = unsafeRun(uio1)
    val exit2 = unsafeRun(uio2)
    (exit1 eqv exit2) || {
      println(s"$exit1 was not equal to $exit2")
      false
    }
  }

  implicit def eqForURIO[R: Arbitrary: Tag, A: Eq](implicit ticker: Ticker): Eq[URIO[R, A]] =
    eqForZIO[R, Nothing, A]

  implicit def execTask(task: Task[Boolean])(implicit ticker: Ticker): Prop =
    ZLayer.succeed(testClock).apply(task).toEffect[CIO]

  implicit def orderForUIOofFiniteDuration(implicit ticker: Ticker): Order[UIO[FiniteDuration]] =
    Order.by(unsafeRun(_).toEither.toOption)

  implicit def orderForRIOofFiniteDuration[R: Arbitrary: Tag](implicit
    ticker: Ticker
  ): Order[RIO[R, FiniteDuration]] =
    (x, y) =>
      Arbitrary
        .arbitrary[ZEnvironment[R]]
        .sample
        .fold(0)(r => x.orDie.provideEnvironment(r) compare y.orDie.provideEnvironment(r))

  implicit def eqForUManaged[A: Eq](implicit ticker: Ticker): Eq[UManaged[A]] =
    zManagedEq[Any, Nothing, A]

  implicit def eqForURManaged[R: Arbitrary: Tag, A: Eq](implicit
    ticker: Ticker
  ): Eq[URManaged[R, A]] =
    zManagedEq[R, Nothing, A]

  implicit def cogenZIO[R: Arbitrary: Tag, E: Cogen, A: Cogen](implicit
    ticker: Ticker
  ): Cogen[ZIO[R, E, A]] =
    Cogen[Outcome[Option, E, A]].contramap { (zio: ZIO[R, E, A]) =>
      Arbitrary.arbitrary[ZEnvironment[R]].sample match {
        case Some(r) =>
          val result = unsafeRun(zio.provideEnvironment(r))

          result match {
            case Exit.Failure(cause) if cause.isInterrupted => Outcome.canceled[Option, E, A]
            case Exit.Failure(cause)                        => Outcome.errored(cause.failureOption.get)
            case Exit.Success(value)                        => Outcome.succeeded(value)
          }
        case None    => Outcome.succeeded(None)
      }
    }

  implicit def cogenOutcomeZIO[R, A](implicit
    cogen: Cogen[ZIO[R, Throwable, A]]
  ): Cogen[Outcome[ZIO[R, Throwable, *], Throwable, A]] =
    cogenOutcome[RIO[R, *], Throwable, A]
}

private[interop] sealed trait CatsSpecBaseLowPriority { this: CatsSpecBase =>

  implicit def eqForIO[E: Eq, A: Eq](implicit ticker: Ticker): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def eqForZIO[R: Arbitrary: Tag, E: Eq, A: Eq](implicit ticker: Ticker): Eq[ZIO[R, E, A]] =
    (x, y) =>
      Arbitrary.arbitrary[ZEnvironment[R]].sample.exists(r => x.provideEnvironment(r) eqv y.provideEnvironment(r))

  implicit def eqForRIO[R: Arbitrary: Tag, A: Eq](implicit ticker: Ticker): Eq[RIO[R, A]] =
    eqForZIO[R, Throwable, A]

  implicit def eqForTask[A: Eq](implicit ticker: Ticker): Eq[Task[A]] =
    eqForIO[Throwable, A]

  def zManagedEq[R, E, A](implicit zio: Eq[ZIO[R, E, A]]): Eq[ZManaged[R, E, A]] =
    Eq.by(managed =>
      ZManaged.ReleaseMap.make.flatMap(rm => ZManaged.currentReleaseMap.locally(rm)(managed.zio.map(_._2)))
    )

  implicit def eqForRManaged[R: Arbitrary: Tag, A: Eq](implicit ticker: Ticker): Eq[RManaged[R, A]] =
    zManagedEq[R, Throwable, A]

  implicit def eqForManaged[E: Eq, A: Eq](implicit ticker: Ticker): Eq[Managed[E, A]] =
    zManagedEq[Any, E, A]

  implicit def eqForZManaged[R: Arbitrary: Tag, E: Eq, A: Eq](implicit
    ticker: Ticker
  ): Eq[ZManaged[R, E, A]] =
    zManagedEq[R, E, A]

  implicit def eqForTaskManaged[A: Eq](implicit ticker: Ticker): Eq[TaskManaged[A]] =
    zManagedEq[Any, Throwable, A]

  implicit def arbitraryZEnvironment[R: Arbitrary: Tag]: Arbitrary[ZEnvironment[R]] =
    Arbitrary(Arbitrary.arbitrary[R].map(ZEnvironment(_)))
}
