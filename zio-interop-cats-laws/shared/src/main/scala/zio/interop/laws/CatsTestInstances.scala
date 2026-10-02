package zio.interop.laws

import cats.effect.testkit.TestInstances
import cats.effect.kernel.Outcome
import cats.effect.IO as CIO
import cats.syntax.all.*
import cats.{ Eq, Id, Order }
import org.scalacheck.{ Arbitrary, Cogen, Gen, Prop }
import zio.*
import zio.interop.{ signalOnNoExternalInterrupt, toOutcomeCauseOtherFiber, toOutcomeOtherFiber0, ToEffectSyntax }
import zio.managed.*

import java.time.temporal.ChronoUnit
import java.time.{ Instant, LocalDateTime, OffsetDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.{ FiniteDuration, TimeUnit }
import scala.language.implicitConversions

/**
 * `Eq`, `Order` and `Cogen` instances for ZIO data types and a `ZIO[Any, E, Boolean] => Prop` conversion
 * (`execZIO`), for use with cats/cats-effect law tests.
 *
 * Effects are compared by running them deterministically on the `TestContext` of an implicit cats-effect `Ticker`
 * (see `cats.effect.testkit.TestInstances`), via `runtime` and `unsafeRun`.
 *
 * `Arbitrary` instances for ZIO effects are provided by `ZioTestInstances` and for ZIO streams by
 * `ZStreamTestInstances`.
 */
trait CatsTestInstances extends TestInstances with CatsTestInstancesLowPriority {

  /**
   * A ZIO `Clock` backed by the `TestContext` of the given `Ticker`. `scheduler` is not supported.
   */
  def testClock(implicit ticker: Ticker) = new Clock {

    def instant(implicit trace: Trace): UIO[Instant]                     =
      ZIO.succeed(Instant.ofEpochMilli(ticker.ctx.now().toMillis))
    def localDateTime(implicit trace: Trace): UIO[LocalDateTime]         =
      ZIO.succeed(LocalDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(ticker.ctx.now().toUnit(unit).toLong)

    def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
      ZIO.succeed(unit.between(Instant.EPOCH, Instant.ofEpochMilli(ticker.ctx.now().toMillis)))

    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))

    def javaClock(implicit trace: zio.Trace): zio.UIO[java.time.Clock] =
      ZIO.succeed(new java.time.Clock {
        def getZone: java.time.ZoneId                                  = ZoneOffset.UTC
        override def withZone(zone: java.time.ZoneId): java.time.Clock = java.time.Clock.fixed(instant(), zone)
        override def instant(): Instant                                = Instant.ofEpochMilli(ticker.ctx.now().toMillis)
      })

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
      ZIO.dieMessage("Scheduler is not supported by the Ticker-based test clock")
  }

  /**
   * Runs `io` on `runtime`, advancing the `Ticker` until no tasks are left.
   *
   * @return
   *   the exit value (`Success(None)` if `io` did not complete) and whether `io` was interrupted externally (i.e. not
   *   by itself)
   */
  def unsafeRun[E, A](io: IO[E, A])(implicit ticker: Ticker): (Exit[E, Option[A]], Boolean) =
    try {
      var exit: Exit[E, Option[A]] = Exit.succeed(Option.empty[A])
      var interrupted: Boolean     = true
      Unsafe.unsafe { implicit u =>
        val fiber = runtime.unsafe.fork[E, Option[A]](signalOnNoExternalInterrupt(io)(ZIO.succeed {
          interrupted = false
        }).asSome)
        fiber.unsafe.addObserver(exit = _)
      }
      tickAll(FiniteDuration(1, TimeUnit.SECONDS))
      (exit, interrupted)
    } catch {
      case error: Throwable =>
        error.printStackTrace()
        throw error
    }

  @tailrec
  private def tickAll(time: FiniteDuration)(implicit ticker: Ticker): Unit = {
    ticker.ctx.advanceAndTick(time)

    if (ticker.ctx.state.tasks.nonEmpty) {
      tickAll(time)
    }
  }

  /**
   * A ZIO `Runtime` that executes on the `TestContext` of the given `Ticker` and uses `testClock`.
   */
  implicit def runtime(implicit ticker: Ticker): Runtime[Any] = {
    val tickerExecutor = Executor.fromExecutionContext(ticker.ctx)
    val fiberId        = FiberId.generate(FiberRefs.empty)(Trace.empty)(Unsafe.unsafe)
    val fiberRefs      = FiberRefs.empty
      .updatedAs(fiberId)(FiberRef.overrideExecutor, Some(tickerExecutor))
      .updatedAs(fiberId)(FiberRef.currentBlockingExecutor, tickerExecutor)
      .updatedAs(fiberId)(DefaultServices.currentServices, DefaultServices.live.add[Clock](testClock))
    val runtimeFlags   = RuntimeFlags.default
    Runtime(ZEnvironment.empty, fiberRefs, runtimeFlags)
  }

  implicit val arbitraryAny: Arbitrary[Any] =
    Arbitrary(Gen.const(()))

  implicit def arbitraryChunk[A: Arbitrary]: Arbitrary[Chunk[A]] =
    Arbitrary(Gen.listOf(Arbitrary.arbitrary[A]).map(Chunk.fromIterable))

  implicit val cogenForAny: Cogen[Any] =
    Cogen(_.hashCode.toLong)

  implicit val eqForNothing: Eq[Nothing] =
    Eq.allEqual

  implicit val eqForCauseOfNothing: Eq[Cause[Nothing]] =
    (x, y) => (x.isInterrupted && y.isInterrupted && x.failureOption.isEmpty && y.failureOption.isEmpty) || x == y

  implicit def eqForUIO[A: Eq](implicit ticker: Ticker): Eq[UIO[A]] = { (uio1, uio2) =>
    val (exit1, i1) = unsafeRun(uio1)
    val (exit2, i2) = unsafeRun(uio2)
    val out1        = toOutcomeCauseOtherFiber[Id, Nothing, Option[A]](i1)(identity, exit1)
    val out2        = toOutcomeCauseOtherFiber[Id, Nothing, Option[A]](i2)(identity, exit2)
    (out1 eqv out2) || {
      println(s"$out1 was not equal to $out2")
      false
    }
  }

  implicit def eqForURIO[R: Arbitrary: Tag, A: Eq](implicit ticker: Ticker): Eq[URIO[R, A]] =
    eqForZIO[R, Nothing, A]

  /**
   * Converts a ZIO effect to a ScalaCheck `Prop`, as required by the cats-effect law tests (the ZIO counterpart of
   * `ioBooleanToProp` in `cats.effect.testkit.TestInstances`).
   */
  implicit def execZIO[E](zio: ZIO[Any, E, Boolean])(implicit ticker: Ticker): Prop =
    zio
      .provideEnvironment(ZEnvironment(()))
      .mapError {
        case t: Throwable => t
        case e            => FiberFailure(Cause.Fail(e, StackTrace.none))
      }
      .toEffect[CIO]

  implicit def orderForUIOofFiniteDuration(implicit ticker: Ticker): Order[UIO[FiniteDuration]] =
    Order.by(unsafeRun(_)._1.toEither.toOption)

  implicit def orderForRIOofFiniteDuration[R: Arbitrary: Tag](implicit ticker: Ticker): Order[RIO[R, FiniteDuration]] =
    (x, y) =>
      Arbitrary
        .arbitrary[ZEnvironment[R]]
        .sample
        .fold(0)(r => orderForUIOofFiniteDuration.compare(x.orDie.provideEnvironment(r), y.orDie.provideEnvironment(r)))

  implicit def orderForZIOofFiniteDuration[E: Order, R: Arbitrary: Tag](implicit
    ticker: Ticker
  ): Order[ZIO[R, E, FiniteDuration]] = {
    implicit val orderForIOofFiniteDuration: Order[IO[E, FiniteDuration]] =
      Order.by(unsafeRun(_)._1 match {
        case Exit.Success(value) => Right(value)
        case Exit.Failure(cause) => Left(cause.failureOption)
      })

    (x, y) =>
      Arbitrary.arbitrary[ZEnvironment[R]].sample.fold(0)(r => x.provideEnvironment(r) compare y.provideEnvironment(r))
  }

  implicit def cogenZIO[R: Arbitrary: Tag, E: Cogen, A: Cogen](implicit
    ticker: Ticker
  ): Cogen[ZIO[R, E, A]] =
    Cogen[Outcome[Option, E, A]].contramap { (zio: ZIO[R, E, A]) =>
      Arbitrary.arbitrary[ZEnvironment[R]].sample match {
        case Some(r) =>
          val (result, extInterrupted) = unsafeRun(zio.provideEnvironment(r))

          result match {
            case Exit.Failure(cause) =>
              if (cause.isInterrupted && extInterrupted) Outcome.canceled[Option, E, A]
              else Outcome.errored(cause.failureOption.get)
            case Exit.Success(value) => Outcome.succeeded(value)
          }
        case None    => Outcome.succeeded(None)
      }
    }

  implicit def cogenOutcomeZIO[R, A](implicit
    cogen: Cogen[ZIO[R, Throwable, A]]
  ): Cogen[Outcome[ZIO[R, Throwable, _], Throwable, A]] =
    cogenOutcome[RIO[R, _], Throwable, A]
}

sealed trait CatsTestInstancesLowPriority { this: CatsTestInstances =>

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

  implicit def eqForCauseOf[E: Eq]: Eq[Cause[E]] = { (exit1, exit2) =>
    val out1 =
      toOutcomeOtherFiber0[Id, E, Either[E, Cause[Nothing]], Unit](true)(identity, Exit.Failure(exit1))(
        (e, _) => Left(e),
        Right(_)
      )
    val out2 =
      toOutcomeOtherFiber0[Id, E, Either[E, Cause[Nothing]], Unit](true)(identity, Exit.Failure(exit2))(
        (e, _) => Left(e),
        Right(_)
      )
    (out1 eqv out2) || {
      println(s"cause $out1 was not equal to cause $out2")
      false
    }
  }

  implicit def arbitraryZEnvironment[R: Arbitrary: Tag]: Arbitrary[ZEnvironment[R]] =
    Arbitrary(Arbitrary.arbitrary[R].map(ZEnvironment(_)))

  implicit def cogenZEnvironment[R: Cogen: Tag]: Cogen[ZEnvironment[R]] =
    Cogen[R].contramap(_.get)

  implicit def eqForZEnvironment[R]: Eq[ZEnvironment[R]] =
    Eq.fromUniversalEquals
}
