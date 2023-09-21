package zio.interop

import cats.implicits.*
import cats.mtl.laws.discipline.*
import zio.*
import zio.interop.catz.*
import zio.interop.catz.mtl.*

class CatsMtlSpec extends ZioSpecBase {
  type Ctx   = String
  type Error = String

  checkAllAsync(
    "Ask[ZIO[Ctx, Error, _]]",
    implicit tc => AskTests[ZIO[Ctx, Error, _], Ctx].ask[Ctx]
  )

  checkAllAsync(
    "Local[ZIO[Ctx, Error, _]]",
    implicit tc => LocalTests[ZIO[Ctx, Error, _], Ctx].local[Ctx, Int]
  )

  checkAllAsync(
    "Ask[ZIO[Ctx, Error, _]] with ZEnvironment",
    implicit tc => AskTests[ZIO[Ctx, Error, _], ZEnvironment[Ctx]].ask[Ctx]
  )

  checkAllAsync(
    "Local[ZIO[Ctx, Error, _]] with ZEnvironment",
    implicit tc => LocalTests[ZIO[Ctx, Error, _], ZEnvironment[Ctx]].local[ZEnvironment[Ctx], Int]
  )

  Unsafe.unsafe { implicit unsafe =>
    implicit val f: FiberRef[Ctx] = FiberRef.unsafe.make("")

    checkAllAsync(
      "FiberRef Ask[ZIO[Ctx, Error, _]]",
      implicit tc => AskTests[ZIO[Ctx, Error, _], Ctx].ask[Ctx]
    )

    checkAllAsync(
      "FiberRef Local[ZIO[Ctx, Error, _]]",
      implicit tc => LocalTests[ZIO[Ctx, Error, _], Ctx].local[Ctx, Int]
    )
  }

  checkAllAsync(
    "Raise[ZIO[Ctx, Error, _]]",
    implicit tc => RaiseTests[ZIO[Ctx, Error, _], Error].raise[Int]
  )

  checkAllAsync(
    "Handle[ZIO[Ctx, Error, _]]",
    implicit tc => HandleTests[ZIO[Ctx, Error, _], Error].handle[Int]
  )
}
