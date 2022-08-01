package zio.interop

import cats.effect.kernel.{ Async, Cont, Sync, Unique }
import zio.{ Exit, Promise, RIO, ZIO }

import scala.concurrent.{ ExecutionContext, Future }

private class ZioAsync[R] extends ZioTemporal[R, Throwable] with Async[RIO[R, _]] {

  override final def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] =
    fa.onExecutionContext(ec)

  override final val executionContext: F[ExecutionContext] =
    ZIO.executor.map(_.asExecutionContext)

  override final val unique: F[Unique.Token] =
    ZIO.succeed(new Unique.Token)

  override final def cont[K, Q](body: Cont[F, K, Q]): F[Q] =
    Async.defaultCont(body)(this)

  override final def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = hint match {
    case Sync.Type.Delay                                           => ZIO.attempt(thunk)
    case Sync.Type.Blocking                                        => ZIO.attemptBlocking(thunk)
    case Sync.Type.InterruptibleOnce | Sync.Type.InterruptibleMany => ZIO.attemptBlockingInterrupt(thunk)
  }

  override final def delay[A](thunk: => A): F[A] =
    ZIO.attempt(thunk)

  override final def defer[A](thunk: => F[A]): F[A] =
    ZIO.suspend(thunk)

  override final def blocking[A](thunk: => A): F[A] =
    ZIO.attemptBlocking(thunk)

  override final def interruptible[A](many: Boolean)(thunk: => A): F[A] =
    ZIO.attemptBlockingInterrupt(thunk)

  override final def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A] =
    for {
      cancelerPromise <- Promise.make[Nothing, Option[F[Unit]]]
      res             <- ZIO
                           .asyncZIO[R, Throwable, A] { resume =>
                             k(exitEither => resume(ZIO.fromEither(exitEither))).onExit {
                               case Exit.Success(maybeCanceler) => cancelerPromise.succeed(maybeCanceler)
                               case _: Exit.Failure[?]          => cancelerPromise.succeed(None)
                             }
                           }
                           .onInterrupt(cancelerPromise.await.flatMap(ZIO.foreach(_)(identity)).orDie)
    } yield res

  override final def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] =
    ZIO.async(register => k(register.compose(fromEither)))

  override final def fromFuture[A](fut: F[Future[A]]): F[A] =
    fut.flatMap(f => ZIO.fromFuture(_ => f))

  override final def never[A]: F[A] =
    ZIO.never
}
