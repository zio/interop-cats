package zio.interop

import zio.blocking.Blocking

private[interop] trait VersionSpecific {
  type CBlocking = Blocking
}
