package zio.interop

import cats.implicits._
import cats.mtl.laws.discipline._
import cats.mtl.{ Ask, Handle, Local, Raise }
import zio._
import zio.interop.catz._
import zio.interop.catz.mtl._

class CatsMtlSpec extends ZioSpecBase {
  type Ctx   = String
  type Error = String

  checkAllAsync(
    "Ask[ZIO[Ctx, Error, *]]",
    implicit tc => AskTests[ZIO[Ctx, Error, *], Ctx].ask[Ctx]
  )

  checkAllAsync(
    "Local[ZIO[Ctx, Error, *]]",
    implicit tc => LocalTests[ZIO[Ctx, Error, *], Ctx].local[Ctx, Int]
  )

  checkAllAsync(
    "Raise[ZIO[Ctx, Error, *]]",
    implicit tc => RaiseTests[ZIO[Ctx, Error, *], Error].raise[Int]
  )

  checkAllAsync(
    "Handle[ZIO[Ctx, Error, *]]",
    implicit tc => HandleTests[ZIO[Ctx, Error, *], Error].handle[Int]
  )

  def askSummoner[R, E]                    = Ask[ZIO[R, E, *], R]
  def askSubtypingSummoner[R1, R <: R1, E] = Ask[ZIO[R, E, *], R1]
  def localSummoner[R, E]                  = Local[ZIO[R, E, *], R]
  def raiseSummoner[R, E]                  = Raise[ZIO[R, E, *], E]
  def handleSummoner[R, E]                 = Handle[ZIO[R, E, *], E]
}
