package zio.interop

import cats.effect.testkit.TestInstances
import cats.syntax.all._
import cats.{ effect, Eq, Order }
import org.scalacheck.{ Arbitrary, Cogen, Gen, Prop }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import zio._
import zio.clock.Clock
import zio.duration._
import zio.internal.{ Executor, Platform, Tracing }

import java.time.{ DateTimeException, Instant, OffsetDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS, TimeUnit }
import scala.language.implicitConversions

private[zio] trait catzSpecBase
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Configuration
    with TestInstances
    with catzSpecBaseLowPriority {

  def checkAllAsync(name: String, f: Ticker => Laws#RuleSet): Unit =
    checkAll(name, f(Ticker()))

  def environment(implicit ticker: Ticker): ZEnv = {
    val testClock = new Clock.Service {
      def currentTime(unit: TimeUnit): UIO[Long] =
        ZIO.effectTotal(unit.convert(Duration.fromScala(ticker.ctx.now())))

      val currentDateTime: IO[DateTimeException, OffsetDateTime] =
        currentTime(MILLISECONDS).map { millis =>
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
        }

      val nanoTime: UIO[Long] =
        ZIO.effectTotal(ticker.ctx.now().toNanos)

      def sleep(duration: Duration): UIO[Unit] = duration.asScala match {
        case finiteDuration: FiniteDuration =>
          ZIO.effectAsyncInterrupt { cb =>
            val canceler = ticker.ctx.schedule(finiteDuration, () => cb(UIO.unit))
            Left(UIO.effectTotal(canceler()))
          }
        case infinite: Infinite =>
          ZIO.dieMessage(s"Unexpected infinite duration $infinite passed to Ticker")
      }
    }

    ZEnv.Services.live ++ Has(testClock)
  }

  implicit def runtime(implicit ticker: Ticker): Runtime[Any] = Runtime(
    (),
    Platform
      .fromExecutor(Executor.fromExecutionContext(1024)(ticker.ctx))
      .withTracing(Tracing.disabled)
      .withReportFailure(_ => ())
  )

  implicit val arbitraryAny: Arbitrary[Any] =
    Arbitrary(Gen.const(()))

  implicit val cogenForAny: Cogen[Any] =
    Cogen(_.hashCode.toLong)

  implicit def arbitraryEnvironment(implicit ticker: Ticker): Arbitrary[ZEnv] =
    Arbitrary(Gen.const(environment))

  implicit val eqForNothing: Eq[Nothing] =
    Eq.allEqual

  implicit val eqForCauseOfNothing: Eq[Cause[Nothing]] =
    Eq.fromUniversalEquals

  implicit def eqForExitOfNothing[A: Eq]: Eq[Exit[Nothing, A]] = {
    case (Exit.Success(x), Exit.Success(y)) => x eqv y
    case (Exit.Failure(x), Exit.Failure(y)) => x eqv y
    case _                                  => false
  }

  implicit def eqForUIO[A: Eq](implicit ticker: Ticker): Eq[UIO[A]] = Eq.by { uio =>
    try {
      var exit: Exit[Nothing, A] = null
      runtime.unsafeRunAsync(uio.flatMap(ZIO.succeedNow).catchAllCause(ZIO.halt(_)))(exit = _)
      ticker.ctx.tickAll(FiniteDuration(1, TimeUnit.SECONDS))
      exit
    } catch {
      case error: Throwable =>
        error.printStackTrace()
        throw error
    }
  }

  implicit def eqForURIO[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URIO[R, A]] =
    eqForZIO[R, Nothing, A]

  implicit def execRIO(rio: RIO[ZEnv, Boolean])(implicit ticker: Ticker): Prop =
    toEffect[effect.IO, Any, Boolean](rio.provide(environment))

  implicit def orderForTaskOfFiniteDuration(implicit ticker: Ticker): Order[Task[FiniteDuration]] =
    Order.by(toEffect[effect.IO, Any, FiniteDuration])

  implicit def orderForRIOofFiniteDuration[R: Arbitrary](implicit ticker: Ticker): Order[RIO[R, FiniteDuration]] =
    (x, y) => Arbitrary.arbitrary[R].sample.fold(0)(r => x.provide(r) compare y.provide(r))

  implicit def eqForUManaged[A: Eq](implicit ticker: Ticker): Eq[UManaged[A]] =
    zManagedEq[Any, Nothing, A]

  implicit def eqForURManaged[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URManaged[R, A]] =
    zManagedEq[R, Nothing, A]
}

private[interop] sealed trait catzSpecBaseLowPriority { this: catzSpecBase =>

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
