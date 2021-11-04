package zio.interop

private[interop] object PlatformSpecific {
  type CBlocking        = Any
  type CBlockingService = AnyRef
}
