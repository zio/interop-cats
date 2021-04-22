package zio.interop

private[interop] trait PlatformSpecific {
  type CBlocking        = Any
  type CBlockingService = AnyRef
}
