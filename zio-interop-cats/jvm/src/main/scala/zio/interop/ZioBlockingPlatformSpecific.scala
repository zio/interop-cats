package zio.interop

import cats.effect.kernel.Sync
import zio.{ RIO, ZIO }

private abstract class ZioBlockingPlatformSpecific[R]
    extends ZioTemporal[R, Throwable, Throwable]
    with Sync[RIO[R, _]]
    with ZioMonadErrorExitThrowable[R] {

  override def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = hint match {
    case Sync.Type.Delay                                           => ZIO.attempt(thunk)
    case Sync.Type.Blocking                                        => ZIO.attemptBlocking(thunk)
    case Sync.Type.InterruptibleOnce | Sync.Type.InterruptibleMany => ZIO.attemptBlockingInterrupt(thunk)
  }

  override def blocking[A](thunk: => A): F[A] =
    ZIO.attemptBlocking(thunk)

  override def interruptible[A](thunk: => A): F[A] =
    ZIO.attemptBlockingInterrupt(thunk)

}
