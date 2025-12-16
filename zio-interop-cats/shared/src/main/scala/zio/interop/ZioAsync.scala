package zio.interop

import cats.effect.kernel.{ Async, Cont, Sync }
import zio.{ RIO, ZIO }

import scala.concurrent.{ ExecutionContext, Future }

private class ZioAsync[R] extends ZioBlockingPlatformSpecific[R] with Async[RIO[R, _]] {

  override def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] =
    fa.onExecutionContext(ec)

  override def executionContext: F[ExecutionContext] =
    ZIO.executor.map(_.asExecutionContext)

  override def cont[K, Q](body: Cont[F, K, Q]): F[Q] =
    Async.defaultCont(body)(this)

  override def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = hint match {
    case Sync.Type.Delay                                           => ZIO.attempt(thunk)
    case Sync.Type.Blocking                                        => ZIO.attemptBlocking(thunk)
    case Sync.Type.InterruptibleOnce | Sync.Type.InterruptibleMany => ZIO.attemptBlockingInterrupt(thunk)
  }

  override def defer[A](thunk: => F[A]): F[A] =
    ZIO.suspend(thunk)

  override def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A] =
    ZIO.suspendSucceed {
      val p = scala.concurrent.Promise[Either[Throwable, A]]()

      def get: F[A] =
        ZIO.fromFuture(_ => p.future).flatMap[Any, Throwable, A](ZIO.fromEither(_))

      ZIO.uninterruptibleMask(restore =>
        k({ e => p.trySuccess(e); () }).flatMap {
          case Some(canceler) => onCancel(restore(get), canceler)
          case None           =>
            // uninterruptible because of https://github.com/typelevel/cats-effect/issues/3725
            get
        }
      )
    }

  override def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] =
    ZIO.async(register => k(register.compose(fromEither)))

  override def fromFuture[A](fut: F[Future[A]]): F[A] =
    fut.flatMap(f => ZIO.fromFuture(_ => f))

  override def never[A]: F[A] =
    ZIO.never
}
