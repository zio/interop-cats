package zio.stream.interop

import zio.interop.CatsSpecBase
import zio.interop.laws.ZStreamTestInstances

private[interop] trait ZStreamSpecBase extends CatsSpecBase with ZStreamTestInstances
