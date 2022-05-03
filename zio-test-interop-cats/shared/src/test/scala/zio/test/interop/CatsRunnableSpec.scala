package zio.interop

import cats.effect.std.Dispatcher
import cats.effect.unsafe.{ IORuntime, IORuntimeConfig, Scheduler }
import cats.effect.IO as CIO
import zio.*
import zio.test.{ TestAspect, ZIOSpecDefault }

import scala.util.Success

abstract class CatsRunnableSpec extends ZIOSpecDefault {
  private[this] var openDispatcher: Dispatcher[CIO] = _
  private[this] var closeDispatcher: CIO[Unit]      = _

  implicit val zioRuntime: Runtime[Any] =
    Runtime.default

  implicit val cioRuntime: IORuntime =
    Scheduler.createDefaultScheduler() match {
      case (scheduler, shutdown) =>
        val ec = zioRuntime.executor.asExecutionContext
        IORuntime(ec, ec, scheduler, shutdown, IORuntimeConfig())
    }

  implicit val dispatcher: Dispatcher[CIO] = new Dispatcher[CIO] {
    override def unsafeToFutureCancelable[A](fa: CIO[A]) =
      openDispatcher.unsafeToFutureCancelable(fa)
  }

  runtime.unsafeRunToFuture {
    ZIO.fromFuture { implicit ec =>
      Dispatcher[CIO].allocated.unsafeToFuture().andThen { case Success((dispatcher, close)) =>
        openDispatcher = dispatcher
        closeDispatcher = close
      }
    }.orDie
  }

  override val aspects = Chunk(
    TestAspect.timeout(1.minute),
    TestAspect.afterAll(ZIO.fromFuture(_ => closeDispatcher.unsafeToFuture()).orDie)
  )
}
