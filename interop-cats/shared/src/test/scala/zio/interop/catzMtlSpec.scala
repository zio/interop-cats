package zio.interop

import cats.implicits._
import cats.mtl.laws.discipline._
import cats.mtl.{ ApplicativeAsk, ApplicativeHandle, ApplicativeLocal, FunctorRaise }
import zio._
import zio.interop.catz._
import zio.interop.catz.mtl._

class catzMtlSpec extends catzSpecZIOBase {
  type Ctx   = String
  type Error = String

  checkAllAsync(
    "ApplicativeAsk[ZIO[Ctx, Error, *]]",
    implicit tc => ApplicativeAskTests[ZIO[Ctx, Error, *], Ctx].applicativeAsk[Ctx]
  )

  checkAllAsync(
    "ApplicativeLocal[ZIO[Ctx, Error, *]]",
    implicit tc => ApplicativeLocalTests[ZIO[Ctx, Error, *], Ctx].applicativeLocal[Ctx, Int]
  )

  checkAllAsync(
    "FunctorRaise[ZIO[Ctx, Error, *]]",
    implicit tc => FunctorRaiseTests[ZIO[Ctx, Error, *], Error].functorRaise[Int]
  )

  checkAllAsync(
    "ApplicativeHandle[ZIO[Ctx, Error, *]]",
    implicit tc => ApplicativeHandleTests[ZIO[Ctx, Error, *], Error].applicativeHandle[Int]
  )

  def askSummoner[R, E]    = ApplicativeAsk[ZIO[R, E, *], R]
  def localSummoner[R, E]  = ApplicativeLocal[ZIO[R, E, *], R]
  def raiseSummoner[R, E]  = FunctorRaise[ZIO[R, E, *], E]
  def handleSummoner[R, E] = ApplicativeHandle[ZIO[R, E, *], E]
}
