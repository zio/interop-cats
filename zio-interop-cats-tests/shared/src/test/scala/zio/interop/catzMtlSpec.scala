package zio.interop

import cats.implicits._
import cats.mtl.laws.discipline._
import cats.mtl.{ Handle, Raise }
import zio._
import zio.interop.catz._
import zio.interop.catz.mtl._

class catzMtlSpec extends catzSpecZIOBase {
  type Ctx   = String
  type Error = String

  checkAllAsync(
    "Raise[ZIO[Ctx, Error, *]]",
    implicit tc => RaiseTests[ZIO[Ctx, Error, *], Error].raise[Int]
  )

  checkAllAsync(
    "Handle[ZIO[Ctx, Error, *]]",
    implicit tc => HandleTests[ZIO[Ctx, Error, *], Error].handle[Int]
  )

  def raiseSummoner[R, E]  = Raise[ZIO[R, E, *], E]
  def handleSummoner[R, E] = Handle[ZIO[R, E, *], E]
}
