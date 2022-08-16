package zio.interop

import zio.{ NeedsEnv, RIO }
import zio.blocking.Blocking

private[interop] object PlatformSpecific {
  type CBlocking        = Blocking
  type CBlockingService = Blocking.Service

  @inline private[interop] def provideBlocking[A](environment: CBlocking)(fa: RIO[CBlocking, A]): RIO[Any, A] =
    fa.provide(environment)(NeedsEnv.needsEnv[CBlocking])
}
