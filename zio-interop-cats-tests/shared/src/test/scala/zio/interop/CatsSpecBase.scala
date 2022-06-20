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
import zio.clock.Clock
import zio.duration.*
import zio.internal.{ Executor, Platform, Tracing }

import java.time.{ DateTimeException, Instant, OffsetDateTime, ZoneOffset }
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

  def platform(implicit ticker: Ticker): Platform =
    Platform
      .fromExecutor(Executor.fromExecutionContext(1024)(ticker.ctx))
      .withTracing(Tracing.disabled)
      .withReportFailure(_ => ())

  def environment(implicit ticker: Ticker): ZEnv = {

    val testBlocking = new CBlockingService {
      def blockingExecutor: Executor =
        Executor.fromExecutionContext(1024)(ticker.ctx)
    }

    val testClock = new Clock.Service {
      def currentTime(unit: TimeUnit): UIO[Long] =
        ZIO.effectTotal(ticker.ctx.now().toUnit(unit).toLong)

      val currentDateTime: IO[DateTimeException, OffsetDateTime] =
        ZIO.effectTotal(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))

      val nanoTime: UIO[Long] =
        ZIO.effectTotal(ticker.ctx.now().toNanos)

      def sleep(duration: Duration): UIO[Unit] = duration.asScala match {
        case finite: FiniteDuration =>
          ZIO.effectAsyncInterrupt { cb =>
            val cancel = ticker.ctx.schedule(finite, () => cb(UIO.unit))
            Left(UIO.effectTotal(cancel()))
          }
        case infinite: Infinite     =>
          ZIO.dieMessage(s"Unexpected infinite duration $infinite passed to Ticker")
      }
    }

    ZEnv.Services.live ++ Has(testClock) ++ Has(testBlocking)
  }

  def unsafeRun[E, A](io: IO[E, A])(implicit ticker: Ticker): Exit[E, Option[A]] =
    try {
      var exit: Exit[E, Option[A]] = Exit.succeed(Option.empty[A])
      runtime.unsafeRunAsync[E, Option[A]](io.asSome)(exit = _)
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
    eqForCauseOf[Nothing]

  implicit def eqForCauseOf[E]: Eq[Cause[E]] =
    (x, y) => (x.interrupted && y.interrupted) || x == y

  implicit def eqForExitOfNothing[A: Eq]: Eq[Exit[Nothing, A]] = {
    case (Exit.Success(x), Exit.Success(y)) => x eqv y
    case (Exit.Failure(x), Exit.Failure(y)) => x eqv y
    case _                                  => false
  }

  implicit def eqForUIO[A: Eq](implicit ticker: Ticker): Eq[UIO[A]] = { (uio1, uio2) =>
    val exit1 = unsafeRun(uio1)
    val exit2 = unsafeRun(uio2)
//    println(s"comparing $exit1 $exit2")
    (exit1 eqv exit2) || {
      println(s"$exit1 was not equal to $exit2")
      false
    }
  }

  implicit def eqForURIO[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URIO[R, A]] =
    eqForZIO[R, Nothing, A]

  implicit def execZIO[E](rio: ZIO[ZEnv, E, Boolean])(implicit ticker: Ticker): Prop =
    rio
      .provide(environment)
      .mapError {
        case t: Throwable => t
        case e            => FiberFailure(Cause.Fail(e))
      }
      .toEffect[CIO]

  implicit def orderForUIOofFiniteDuration(implicit ticker: Ticker): Order[UIO[FiniteDuration]] =
    Order.by(unsafeRun(_).toEither.toOption)

  implicit def orderForRIOofFiniteDuration[R: Arbitrary](implicit ticker: Ticker): Order[RIO[R, FiniteDuration]] =
    (x, y) =>
      Arbitrary
        .arbitrary[R]
        .sample
        .fold(0)(r => orderForUIOofFiniteDuration.compare(x.orDie.provide(r), y.orDie.provide(r)))

  implicit def orderForZIOofFiniteDuration[E: Order, R: Arbitrary](implicit
    ticker: Ticker
  ): Order[ZIO[R, E, FiniteDuration]] = {
    implicit val orderForIOofFiniteDuration: Order[IO[E, FiniteDuration]] =
      Order.by(unsafeRun(_) match {
        case Exit.Success(value) => Right(value)
        case Exit.Failure(cause) => Left(cause.failureOption)
      })

    (x, y) => Arbitrary.arbitrary[R].sample.fold(0)(r => x.provide(r) compare y.provide(r))
  }

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
            case Exit.Failure(cause) =>
              if (cause.interrupted) Outcome.canceled[Option, E, A]
              else Outcome.errored(cause.failureOption.get)
            case Exit.Success(value) => Outcome.succeeded(value)
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

  def zManagedEq[R, E, A](implicit zio: Eq[ZIO[R, E, A]]): Eq[ZManaged[R, E, A]] =
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
