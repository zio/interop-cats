package zio.interop

import zio.RIO

import scala.annotation.unused

private[interop] object PlatformSpecific {
  type CBlocking        = Any
  type CBlockingService = AnyRef

  @inline private[interop] def provideBlocking[A](@unused environment: CBlocking)(fa: RIO[CBlocking, A]): RIO[Any, A] =
    fa
}
