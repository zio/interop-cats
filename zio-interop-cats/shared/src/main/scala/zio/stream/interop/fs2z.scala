package zio.stream.interop

import fs2.io.net.Network
import fs2.io.file.Files
import fs2.io.net.unixsocket.UnixSockets
import zio.*
import zio.interop.CatsEffectInstances

object fs2z extends FS2Platform {
  object io extends FS2IOPlatform
}

abstract class FS2Platform extends FS2StreamSyntax

abstract class FS2IOPlatform extends FS2IOFilesInstances with FS2IONetworkInstances with FS2IOUnixSocketsInstances

trait FS2IOFilesInstances extends CatsEffectInstances {

  implicit final def filesInstance[R]: Files[RIO[R, _]] =
    Files.forAsync(asyncInstance[R])

}

trait FS2IONetworkInstances extends CatsEffectInstances {

  implicit final def networkInstance[R]: Network[RIO[R, _]] =
    Network.forAsync(asyncInstance[R])

}

trait FS2IOUnixSocketsInstances extends CatsEffectInstances {

  implicit final def unixSocketsInstance[R]: UnixSockets[RIO[R, _]] =
    UnixSockets.forAsync(asyncInstance[R])

}
