package zio.interop

import cats.effect.kernel.Unique
import cats.effect.{ Async, Cont, Sync }
import zio.clock.Clock
import zio.internal.Executor
import zio.{ RIO, ZIO }

import scala.concurrent.{ ExecutionContext, Future }

private class ZioAsync[R <: Clock] extends ZioTemporal[R, Throwable] with Async[RIO[R, *]] {

  override final def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] =
    ZIO.effectSuspendTotalWith { (platform, _) =>
      fa.lock(Executor.fromExecutionContext(platform.executor.yieldOpCount)(ec))
    }

  override final val executionContext: F[ExecutionContext] =
    ZIO.effectSuspendTotalWith { (platform, _) =>
      ZIO.succeedNow(platform.executor.asEC)
    }

  override final val unique: F[Unique.Token] =
    ZIO.effectTotal(new Unique.Token)

  override final def cont[K, Q](body: Cont[F, K, Q]): F[Q] =
    Async.defaultCont(body)(this)

  override final def suspend[A](hint: Sync.Type)(thunk: => A): F[A] =
    ZIO.effect(thunk)

  override final def delay[A](thunk: => A): F[A] =
    ZIO.effect(thunk)

  override final def defer[A](thunk: => F[A]): F[A] =
    ZIO.effectSuspend(thunk)

  override final def blocking[A](thunk: => A): F[A] =
    ZIO.effect(thunk)

  override final def interruptible[A](many: Boolean)(thunk: => A): F[A] =
    ZIO.effect(thunk)

  override final def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A] =
    ZIO.effectAsyncM(register => k(register.compose(fromEither)))

  override final def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] =
    ZIO.effectAsync(register => k(register.compose(fromEither)))

  override final def fromFuture[A](fut: F[Future[A]]): F[A] =
    fut.flatMap(f => ZIO.fromFuture(_ => f))

  override final def never[A]: F[A] =
    ZIO.never
}
