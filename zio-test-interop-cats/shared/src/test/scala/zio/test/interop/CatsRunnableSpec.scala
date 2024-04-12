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
        Unsafe.unsafe { implicit u =>
          val ec = zioRuntime.unsafe.run(ZIO.executor.map(_.asExecutionContext)).getOrThrowFiberFailure()
          IORuntime(ec, ec, scheduler, shutdown, IORuntimeConfig())
        }
    }

  implicit val dispatcher: Dispatcher[CIO] = new Dispatcher[CIO] {
    override def unsafeToFutureCancelable[A](fa: CIO[A]) =
      openDispatcher.unsafeToFutureCancelable(fa)
  }

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.runToFuture {
      ZIO.fromFuture { implicit ec =>
        Dispatcher.parallel[CIO].allocated.unsafeToFuture().andThen { case Success((disp, close)) =>
          openDispatcher = disp
          closeDispatcher = close
        }
      }.orDie
    }
  }

  override val aspects: Chunk[TestAspect[Nothing, Any, Nothing, Any]] = Chunk(
    TestAspect.timeout(1.minute),
    TestAspect.afterAll(ZIO.fromFuture(_ => closeDispatcher.unsafeToFuture()).orDie)
  )
}
