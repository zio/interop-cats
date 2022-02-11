package zio.interop

import cats.effect.kernel.{ Async, Cont, Sync, Unique }
import zio.blocking.{ effectBlocking, effectBlockingInterrupt }
import zio.{ Promise, RIO, ZIO }

import scala.concurrent.{ ExecutionContext, Future }

private abstract class ZioAsync[R]
    extends ZioTemporal[R, Throwable]
    with Async[RIO[R, _]]
    with ZioBlockingEnv[R, Throwable] {

  override final def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] =
    fa.on(ec)

  override final val executionContext: F[ExecutionContext] =
    ZIO.executor.map(_.asEC)

  override final val unique: F[Unique.Token] =
    ZIO.effectTotal(new Unique.Token)

  override final def cont[K, Q](body: Cont[F, K, Q]): F[Q] =
    Async.defaultCont(body)(this)

  override final def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = hint match {
    case Sync.Type.Delay             => ZIO.effect(thunk)
    case Sync.Type.Blocking          => blocking(thunk)
    case Sync.Type.InterruptibleOnce => interruptible(many = false)(thunk)
    case Sync.Type.InterruptibleMany => interruptible(many = true)(thunk)
  }

  override final def delay[A](thunk: => A): F[A] =
    ZIO.effect(thunk)

  override final def defer[A](thunk: => F[A]): F[A] =
    ZIO.effectSuspend(thunk)

  override final def blocking[A](thunk: => A): F[A] =
    withBlocking(effectBlocking(thunk))

  override final def interruptible[A](many: Boolean)(thunk: => A): F[A] =
    withBlocking(effectBlockingInterrupt(thunk))

  override final def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A] =
    Promise.make[Nothing, Unit].flatMap { promise =>
      ZIO.effectAsyncM { register =>
        k(either => register(promise.await *> ZIO.fromEither(either))) *> promise.succeed(())
      }
    }

  override final def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] =
    ZIO.effectAsync(register => k(register.compose(fromEither)))

  override final def fromFuture[A](fut: F[Future[A]]): F[A] =
    fut.flatMap(f => ZIO.fromFuture(_ => f))

  override final def never[A]: F[A] =
    ZIO.never
}
