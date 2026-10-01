package zio.stream.interop

// On Scala Native fs2 constructs `UnixSockets` only via `forLiftIO`, which would require an `IORuntime`;
// `UnixSockets` is deprecated since fs2 3.13.0, use `Network` instead.
trait FS2IOUnixSocketsInstances
