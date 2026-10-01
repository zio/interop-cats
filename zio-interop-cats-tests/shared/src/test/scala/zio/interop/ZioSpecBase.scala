package zio.interop

import zio.interop.laws.ZioTestInstances

private[interop] trait ZioSpecBase extends CatsSpecBase with ZioTestInstances
