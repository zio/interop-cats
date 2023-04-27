package zio.interop

import cats.implicits.*
import cats.mtl.laws.discipline.*
import cats.mtl.{ Handle, Raise }
import zio.*
import zio.interop.catz.*
import zio.interop.catz.mtl.*

class CatsMtlSpec extends ZioSpecBase {
  type Ctx   = String
  type Error = String

  checkAllAsync(
    "Ask[ZIO[Ctx, Error, _]]",
    implicit tc => AskTests[ZIO[Ctx, Error, _], ZEnvironment[Ctx]].ask[Ctx]
  )

  checkAllAsync(
    "Local[ZIO[Ctx, Error, _]]",
    implicit tc => LocalTests[ZIO[Ctx, Error, _], ZEnvironment[Ctx]].local[ZEnvironment[Ctx], Int]
  )

  checkAllAsync(
    "Raise[ZIO[Ctx, Error, _]]",
    implicit tc => RaiseTests[ZIO[Ctx, Error, _], Error].raise[Int]
  )

  checkAllAsync(
    "Handle[ZIO[Ctx, Error, _]]",
    implicit tc => HandleTests[ZIO[Ctx, Error, _], Error].handle[Int]
  )

  def raiseSummoner[R, E]  = Raise[ZIO[R, E, _], E]
  def handleSummoner[R, E] = Handle[ZIO[R, E, _], E]
}
