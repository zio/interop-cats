package zio.interop

import java.util.concurrent.TimeUnit

import cats.effect.{ ContextShift, ExitCase, Resource, IO => CIO }
import cats.syntax.apply._
import cats.syntax.functor._
import org.specs2.Specification
import org.specs2.specification.AroundTimeout
import zio.interop.catz._
import zio.{ Task, _ }

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global

class CatsZManagedSyntaxSpec extends Specification with AroundTimeout {

  val runtime                                = Runtime.default
  def unsafeRun[R, E, A](p: ZIO[ZEnv, E, A]) = runtime.unsafeRun(p)

  def is = s2"""

      toManaged
        calls finalizers correctly when use is interrupted $toManagedFinalizersWhenInterrupted
        calls finalizers correctly when use has failed $toManagedFinalizersWhenFailed
        calls finalizers correctly when use has died $toManagedFinalizersWhenDied
        calls finalizers correctly when using the resource $toManagedFinalizers
        calls finalizers should not run if exception is thrown in acquisition $toManagedFinalizersExceptionAcquisition
        composing with other managed should calls finalizers in correct order $toManagedComposition

      toManagedZIO
        calls finalizers correctly when use is interrupted $toManagedZIOFinalizersWhenInterrupted
        calls finalizers correctly when use has failed $toManagedZIOFinalizersWhenFailed
        calls finalizers correctly when use has died $toManagedZIOFinalizersWhenDied
        calls finalizers correctly when using the resource $toManagedZIOFinalizers
        calls finalizers should not run if exception is thrown in acquisition $toManagedZIOFinalizersExceptionAcquisition
        composing with other managed should calls finalizers in correct order $toManagedZIOComposition

      toResource
        calls finalizers when using resource $toResourceFinalizers
        calls finalizers when using resource fails $toResourceFinalizersWhenFailed
        calls finalizers when using resource is canceled $toResourceFinalizersWhenCanceled
        acquisition of Reservation preserves cancellability in new F $toResourceCancelableReservationAcquisition
      """

  def toManagedFinalizersWhenInterrupted = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.makeCase(CIO.delay { effects += x; () }) {
        case (_, ExitCase.Canceled) =>
          CIO.delay { effects += x + 1; () }
        case _ => CIO.unit
      }

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.interrupt.unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toManagedFinalizersWhenFailed = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.makeCase(CIO.delay { effects += x; () }) {
        case (_, ExitCase.Error(_)) =>
          CIO.delay { effects += x + 1; () }
        case _ =>
          CIO.unit
      }

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.fail(new RuntimeException()).unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toManagedFinalizersWhenDied = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.makeCase(CIO.delay { effects += x; () }) {
        case (_, ExitCase.Error(_)) =>
          CIO.delay { effects += x + 1; () }
        case _ =>
          CIO.unit
      }

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.die(new RuntimeException()).unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toManagedFinalizersExceptionAcquisition = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay(effects += x) *> CIO.delay(throw new RuntimeException()).void)(
        _ => CIO.delay { effects += x + 1; () }
      )

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use_(ZIO.unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1))
  }

  def toManagedFinalizers = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay { effects += x; () })(_ => CIO.delay { effects += x; () })

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use_(ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 1))
  }

  def toManagedComposition = {

    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay { effects += x; () })(_ => CIO.delay { effects += x; () })

    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = {
      val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      val managed2: ZManaged[Any, Throwable, Unit] = man(2)
      (managed1 *> managed2).use_(ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 2, 2, 1))

  }

  def toManagedZIOFinalizersWhenInterrupted = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.makeCase(Task { effects += x; () }) {
        case (_, ExitCase.Canceled) =>
          Task { effects += x + 1; () }
        case _ => Task.unit
      }

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
      managed.use(_ => ZIO.interrupt.unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toManagedZIOFinalizersWhenFailed = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.makeCase(Task { effects += x; () }) {
        case (_, ExitCase.Error(_)) =>
          Task { effects += x + 1; () }
        case _ =>
          Task.unit
      }

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
      managed.use(_ => ZIO.fail(new RuntimeException()).unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toManagedZIOFinalizersWhenDied = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.makeCase(Task { effects += x; () }) {
        case (_, ExitCase.Error(_)) =>
          Task { effects += x + 1; () }
        case _ =>
          Task.unit
      }

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
      managed.use(_ => ZIO.die(new RuntimeException()).unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toManagedZIOFinalizersExceptionAcquisition = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(Task(effects += x) *> Task(throw new RuntimeException()).unit)(
        _ => Task { effects += x + 1; () }
      )

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
      managed.use_(ZIO.unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1))
  }

  def toManagedZIOFinalizers = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(Task { effects += x; () })(_ => Task { effects += x; () })

    val testCase = {
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
      managed.use_(ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 1))
  }

  def toManagedZIOComposition = {

    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[Task, Unit] =
      Resource.make(Task { effects += x; () })(_ => Task { effects += x; () })

    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = {
      val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
      val managed2: ZManaged[Any, Throwable, Unit] = man(2)
      (managed1 *> managed2).use_(ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 2, 2, 1))

  }

  def toResourceFinalizers = {
    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x + 1))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.unit)
    }
    unsafeRun(testCase)
    effects must be_===(List(1, 2))
  }

  def toResourceFinalizersWhenFailed = {
    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.makeExit(ZIO.effectTotal(effects += x).unit) {
        case (_, Exit.Failure(_)) =>
          ZIO.effectTotal(effects += x + 1)
        case _ =>
          ZIO.unit
      }

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.fail(new RuntimeException()).unit)
    }
    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toResourceFinalizersWhenCanceled = {
    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.makeExit(ZIO.effectTotal(effects += x).unit) {
        case (_, e) if e.interrupted =>
          ZIO.effectTotal(effects += x + 1)
        case _ =>
          ZIO.unit
      }

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.interrupt)
    }
    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 2))
  }

  def toResourceCancelableReservationAcquisition =
    unsafeRun {
      ZIO.runtime[Any] >>= { implicit runtime =>
        implicit val ctx: ContextShift[CIO] = CIO.contextShift(global)

        (for {
          startLatch <- Promise.make[Nothing, Unit]
          endLatch   <- Promise.make[Nothing, Unit]
          release    <- Ref.make(false)
          managed = ZManaged.reserve(
            Reservation(
              acquire = startLatch.succeed(()) *> ZIO.never,
              release = _ => release.set(true) *> endLatch.succeed(())
            )
          )
          resource = managed.toResource[CIO]

          _ <- IO {
                resource
                  .use(_ => CIO.unit)
                  .start
                  .flatMap(
                    f =>
                      CIO(runtime.unsafeRun(startLatch.await))
                        .flatMap(_ => f.cancel)
                  )
                  .unsafeRunSync()
              }
          _   <- endLatch.await.timeout(zio.duration.Duration(10, TimeUnit.SECONDS))
          res <- release.get
        } yield res must_=== true).provideLayer(ZEnv.live)
      }
    }

}
