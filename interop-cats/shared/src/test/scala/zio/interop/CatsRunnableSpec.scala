package zio.interop

import cats.effect.std.Dispatcher
import cats.effect.unsafe.{ IORuntime, IORuntimeConfig, Scheduler }
import cats.effect.{ IO => CIO }
import zio._
import zio.duration._
import zio.test.{ DefaultRunnableSpec, TestAspect }

import scala.util.Success

abstract class CatsRunnableSpec extends DefaultRunnableSpec {
  private[this] var openDispatcher: Dispatcher[CIO] = _
  private[this] var closeDispatcher: CIO[Unit]      = _

  implicit val zioRuntime: Runtime[ZEnv] =
    Runtime.default

  implicit val cioRuntime: IORuntime =
    Scheduler.createDefaultScheduler() match {
      case (scheduler, shutdown) =>
        val ec = zioRuntime.platform.executor.asEC
        IORuntime(ec, ec, scheduler, shutdown, IORuntimeConfig())
    }

  implicit val dispatcher: Dispatcher[CIO] = new Dispatcher[CIO] {
    override def unsafeToFutureCancelable[A](fa: CIO[A]) =
      openDispatcher.unsafeToFutureCancelable(fa)
  }

  override val aspects = List(
    TestAspect.timeout(2.seconds),
    TestAspect.beforeAll(ZIO.fromFuture { implicit ec =>
      Dispatcher[CIO].allocated.unsafeToFuture().andThen {
        case Success((dispatcher, close)) =>
          openDispatcher = dispatcher
          closeDispatcher = close
      }
    }.orDie),
    TestAspect.afterAll(ZIO.fromFuture(_ => closeDispatcher.unsafeToFuture()).orDie)
  )
}
