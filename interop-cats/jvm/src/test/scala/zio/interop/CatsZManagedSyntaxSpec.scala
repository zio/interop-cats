package zio.interop

import java.util.concurrent.TimeUnit

import cats.effect.{ Concurrent, ContextShift, Resource, Timer, IO => CIO }
import cats.syntax.apply._
import cats.syntax.functor._
import org.specs2.Specification
import org.specs2.specification.AroundTimeout
import zio.{ DefaultRuntime, IO, Promise, Reservation, UIO, ZIO, ZManaged }
import zio.interop.catz._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.FiniteDuration

class CatsZManagedSyntaxSpec extends Specification with AroundTimeout with DefaultRuntime {

  def is = s2"""

      toManaged
        calls finalizers correctly when use is interrupted $toManagedFinalizersWhenInterrupted
        calls finalizers correctly when use has failed $toManagedFinalizersWhenFailed
        calls finalizers correctly when use has died $toManagedFinalizersWhenDied
        calls finalizers correctly when using the resource $toManagedFinalizers
        calls finalizers should not run if exception is thrown in acquisition $toManagedFinalizersExceptionAcquisition
        composing with other managed should calls finalizers in correct order $toManagedComposition

      toResource
        calls finalizers when using resource $toResourceFinalizers
        calls finalizers when using resource fails $toResourceFinalizersWhenFailed
        calls finalizers when using resource is canceled $toResourceFinalizersWhenCanceled
        acquisition of Reservation preserves cancellability in new F $toResourceCancelableReservationAcquisition
      """

  def toManagedFinalizersWhenInterrupted = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay { effects += x; () })(_ => CIO.delay { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.interrupt.unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toManagedFinalizersWhenFailed = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay { effects += x; () })(_ => CIO.delay { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.fail(new RuntimeException()).unit)
    }

    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toManagedFinalizersWhenDied = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay { effects += x; () })(_ => CIO.delay { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.die(new RuntimeException()).unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toManagedFinalizersExceptionAcquisition = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay(effects += x) *> CIO.delay(throw new RuntimeException()).void)(
        _ => CIO.delay { effects += x; () }
      )

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.unit)
    }

    unsafeRun(testCase.sandbox.orElse(ZIO.unit))
    effects must be_===(List(1))
  }

  def toManagedFinalizers = {
    val effects = new mutable.ListBuffer[Int]
    def res(x: Int): Resource[CIO, Unit] =
      Resource.make(CIO.delay { effects += x; () })(_ => CIO.delay { effects += x; () })

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      managed.use(_ => ZIO.unit)
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

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManaged
      val managed2: ZManaged[Any, Throwable, Unit] = man(2)
      (managed1 *> managed2).use(_ => ZIO.unit)
    }

    unsafeRun(testCase)
    effects must be_===(List(1, 2, 2, 1))

  }

  def toResourceFinalizers = {

    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.unit)
    }
    unsafeRun(testCase)
    effects must be_===(List(1, 1))
  }

  def toResourceFinalizersWhenFailed = {

    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.fail(new RuntimeException()).unit)
    }
    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toResourceFinalizersWhenCanceled = {

    val effects = new mutable.ListBuffer[Int]
    def man(x: Int): ZManaged[Any, Throwable, Unit] =
      ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

    val testCase = ZIO.runtime[Any].flatMap { implicit r =>
      man(1).toResource.use(_ => ZIO.interrupt)
    }
    unsafeRun(testCase.orElse(ZIO.unit))
    effects must be_===(List(1, 1))
  }

  def toResourceCancelableReservationAcquisition =
    unsafeRun {
      ZIO.runtime[Any] >>= { implicit runtime =>
        implicit val ctx: ContextShift[CIO] = CIO.contextShift(global)
        implicit val timer: Timer[CIO]      = CIO.timer(global)

        for {
          latch    <- Promise.make[Nothing, Unit]
          managed  = ZManaged.reserve(Reservation(latch.await, ZIO.unit))
          resource = managed.toResource[CIO]
          res <- IO {
                  Concurrent
                    .timeout(resource.use(_ => CIO.unit), FiniteDuration(0, TimeUnit.SECONDS))
                    .unsafeRunSync()
                }.const(false) orElse UIO(true)
          _ <- latch.succeed(())
        } yield res must_=== true
      }
    }

}
