package zio.interop

import cats.effect.kernel.Sync
import zio.{ RIO, ZIO }

private abstract class ZioBlockingPlatformSpecific[R]
    extends ZioTemporal[R, Throwable, Throwable]
    with Sync[RIO[R, _]]
    with ZioMonadErrorExitThrowable[R] {

  override def delay[A](thunk: => A): F[A] =
    ZIO.attempt(thunk)

  override def blocking[A](thunk: => A): F[A] =
    ZIO.attempt(thunk)

  override def interruptible[A](thunk: => A): F[A] =
    ZIO.attempt(thunk)

}
