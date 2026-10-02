package zio.interop

import cats.effect.std.Dispatcher
import cats.effect.unsafe.{ IORuntime, IORuntimeConfig, Scheduler }
import cats.effect.IO as CIO
import zio.*
import zio.test.{ TestAspect, ZIOSpecDefault }

abstract class CatsRunnableSpec extends ZIOSpecDefault {
  @volatile private[this] var openDispatcher: Dispatcher[CIO] = _

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

  // The dispatcher is allocated before any test runs, so `dispatcher` never sees an unassigned `openDispatcher`.
  override val aspects: Chunk[TestAspect[Nothing, Any, Nothing, Any]] = Chunk(
    TestAspect.timeout(1.minute),
    TestAspect.aroundAllWith(
      ZIO
        .fromFuture(_ => Dispatcher.parallel[CIO].allocated.unsafeToFuture())
        .tap { case (disp, _) => ZIO.succeed { openDispatcher = disp } }
        .orDie
    ) { case (_, close) => ZIO.fromFuture(_ => close.unsafeToFuture()).orDie }
  )
}
