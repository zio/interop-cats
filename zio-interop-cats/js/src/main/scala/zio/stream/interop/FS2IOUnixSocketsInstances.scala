package zio.stream.interop

import fs2.io.net.unixsocket.UnixSockets
import zio.*
import zio.interop.CatsEffectInstances

trait FS2IOUnixSocketsInstances extends CatsEffectInstances {

  implicit final def unixSocketsInstance[R]: UnixSockets[RIO[R, _]] =
    UnixSockets.forAsync(asyncInstance[R])

}
