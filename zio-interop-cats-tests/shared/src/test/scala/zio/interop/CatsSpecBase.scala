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
import zio.internal.tracing.{ Tracing, TracingConfig }

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

  def platform(implicit ticker: Ticker): RuntimeConfig =
    RuntimeConfig
      .fromExecutor(Executor.fromExecutionContext(1024)(ticker.ctx))
      .copy(
        tracing = Tracing(TracingConfig.disabled),
        blockingExecutor = Executor.fromExecutionContext(1024)(ticker.ctx)
      )

  def environment(implicit ticker: Ticker): ZEnv = {

    val testClock = new Clock {

      def instant(implicit trace: ZTraceElement): UIO[Instant]                     =
        ???
      def localDateTime(implicit trace: ZTraceElement): UIO[LocalDateTime]         =
        ???
      def currentTime(unit: => TimeUnit)(implicit trace: ZTraceElement): UIO[Long] =
        ZIO.succeed(ticker.ctx.now().toUnit(unit).toLong)

      def currentDateTime(implicit trace: ZTraceElement): UIO[OffsetDateTime] =
        ZIO.succeed(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))

      def nanoTime(implicit trace: ZTraceElement): UIO[Long] =
        ZIO.succeed(ticker.ctx.now().toNanos)

      def sleep(duration: => Duration)(implicit trace: ZTraceElement): UIO[Unit] = duration.asScala match {
        case finite: FiniteDuration =>
          ZIO.asyncInterrupt { cb =>
            val cancel = ticker.ctx.schedule(finite, () => cb(UIO.unit))
            Left(UIO.succeed(cancel()))
          }
        case infinite: Infinite     =>
          ZIO.dieMessage(s"Unexpected infinite duration $infinite passed to Ticker")
      }
    }

    ZEnv.Services.live ++ Has(testClock)
  }

  def unsafeRun[E, A](io: IO[E, A])(implicit ticker: Ticker): Exit[E, Option[A]] =
    try {
      var exit: Exit[E, Option[A]] = Exit.succeed(Option.empty[A])
      runtime.unsafeRunAsyncWith[E, Option[A]](io.asSome)(exit = _)
      ticker.ctx.tickAll(FiniteDuration(1, TimeUnit.SECONDS))
      exit
    } catch {
      case error: Throwable =>
        error.printStackTrace()
        throw error
    }

  implicit def runtime(implicit ticker: Ticker): Runtime[Any] =
    Runtime((), platform)

  implicit val arbitraryAny: Arbitrary[Any] =
    Arbitrary(Gen.const(()))

  implicit val cogenForAny: Cogen[Any] =
    Cogen(_.hashCode.toLong)

  implicit def arbitraryEnvironment(implicit ticker: Ticker): Arbitrary[ZEnv] =
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

  implicit def eqForURIO[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URIO[R, A]] =
    eqForZIO[R, Nothing, A]

  implicit def execRIO(rio: RIO[ZEnv, Boolean])(implicit ticker: Ticker): Prop =
    rio.provide(environment).toEffect[CIO]

  implicit def orderForUIOofFiniteDuration(implicit ticker: Ticker): Order[UIO[FiniteDuration]] =
    Order.by(unsafeRun(_).toEither.toOption)

  implicit def orderForRIOofFiniteDuration[R: Arbitrary](implicit ticker: Ticker): Order[RIO[R, FiniteDuration]] =
    (x, y) => Arbitrary.arbitrary[R].sample.fold(0)(r => x.orDie.provide(r) compare y.orDie.provide(r))

  implicit def eqForUManaged[A: Eq](implicit ticker: Ticker): Eq[UManaged[A]] =
    zManagedEq[Any, Nothing, A]

  implicit def eqForURManaged[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URManaged[R, A]] =
    zManagedEq[R, Nothing, A]

  implicit def cogenZIO[R: Arbitrary, E: Cogen, A: Cogen](implicit ticker: Ticker): Cogen[ZIO[R, E, A]] =
    Cogen[Outcome[Option, E, A]].contramap { (zio: ZIO[R, E, A]) =>
      Arbitrary.arbitrary[R].sample match {
        case Some(r) =>
          val result = unsafeRun(zio.provide(r))

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

  implicit def eqForZIO[R: Arbitrary, E: Eq, A: Eq](implicit ticker: Ticker): Eq[ZIO[R, E, A]] =
    (x, y) => Arbitrary.arbitrary[R].sample.exists(r => x.provide(r) eqv y.provide(r))

  implicit def eqForRIO[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[RIO[R, A]] =
    eqForZIO[R, Throwable, A]

  implicit def eqForTask[A: Eq](implicit ticker: Ticker): Eq[Task[A]] =
    eqForIO[Throwable, A]

  def zManagedEq[R, E, A](implicit zio: Eq[ZIO[R, E, A]]): Eq[ZManaged[R, E, A]]               =
    Eq.by(managed => ZManaged.ReleaseMap.make.flatMap(rm => managed.zio.provideSome[R](_ -> rm).map(_._2)))

  implicit def eqForRManaged[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[RManaged[R, A]] =
    zManagedEq[R, Throwable, A]

  implicit def eqForManaged[E: Eq, A: Eq](implicit ticker: Ticker): Eq[Managed[E, A]] =
    zManagedEq[Any, E, A]

  implicit def eqForZManaged[R: Arbitrary, E: Eq, A: Eq](implicit ticker: Ticker): Eq[ZManaged[R, E, A]] =
    zManagedEq[R, E, A]

  implicit def eqForTaskManaged[A: Eq](implicit ticker: Ticker): Eq[TaskManaged[A]] =
    zManagedEq[Any, Throwable, A]
}
