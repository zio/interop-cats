package zio.stream.interop

import fs2.io.net.Network
import fs2.io.file.Files
import zio.*
import zio.interop.CatsEffectInstances

object fs2z extends FS2Platform {
  object io extends FS2IOPlatform
}

abstract class FS2Platform extends FS2StreamSyntax

abstract class FS2IOPlatform extends FS2IOFilesInstances with FS2IONetworkInstances

trait FS2IOFilesInstances extends CatsEffectInstances {

  implicit final def filesInstance[R]: Files[RIO[R, _]] =
    Files.forAsync(asyncInstance[R])

}

trait FS2IONetworkInstances extends CatsEffectInstances {

  implicit final def networkInstance[R]: Network[RIO[R, _]] =
    Network.forAsync(asyncInstance[R])

}
