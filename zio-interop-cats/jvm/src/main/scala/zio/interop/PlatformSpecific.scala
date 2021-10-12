package zio.interop

import zio.blocking.Blocking

private[interop] object PlatformSpecific {
  type CBlocking        = Blocking
  type CBlockingService = Blocking.Service
}
