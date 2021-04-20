package zio.interop

import zio.blocking.Blocking

private[interop] trait PlatformSpecific {
  type CBlocking        = Blocking
  type CBlockingService = Blocking.Service
}
