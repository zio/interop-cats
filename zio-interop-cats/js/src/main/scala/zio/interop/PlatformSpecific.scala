package zio.interop

import zio.RIO

private[interop] object PlatformSpecific {
  type CBlocking        = Any
  type CBlockingService = AnyRef

  @inline private[interop] def provideBlocking[A](environment: CBlocking)(fa: RIO[CBlocking, A]): RIO[Any, A] = {
    val _ = environment
    fa
  }
}
