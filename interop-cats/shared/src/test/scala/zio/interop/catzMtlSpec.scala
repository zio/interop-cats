package zio.interop

import cats.Eq
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import cats.mtl.laws.discipline._
import cats.mtl.{ ApplicativeAsk, ApplicativeHandle, ApplicativeLocal, FunctorRaise }
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{ BeforeAndAfterAll, Matchers }
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.internal.PlatformLive
import zio.interop.catz._
import zio.interop.catz.mtl._
import zio.random.Random
import zio.system.System

class catzMtlSpec
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with GeneratorDrivenPropertyChecks
    with Discipline
    with TestInstances
    with GenIO {

  type Env   = Clock with Console with System with Random
  type Ctx   = String
  type Error = String

  implicit def rts(implicit tc: TestContext): Runtime[Env] = new DefaultRuntime {
    override val Platform = PlatformLive.fromExecutionContext(tc).withReportFailure(_ => ())
  }

  implicit val zioEqErrorCause: Eq[Cause[Error]] =
    Eq.fromUniversalEquals

  implicit def zioEq[R: Arbitrary, A: Eq](implicit tc: TestContext): Eq[ZIO[R, Error, A]] = {
    def run(r: R, zio: ZIO[R, Error, A]) = taskEffectInstance.toIO(zio.provide(r).sandbox.either)
    Eq.instance((io1, io2) => Arbitrary.arbitrary[R].sample.fold(false)(r => run(r, io1) eqv run(r, io2)))
  }

  implicit def zioArbitrary[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[R => IO[E, A]].map(ZIO.environment[R].flatMap(_)))

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext()))

  checkAllAsync(
    "ApplicativeAsk[ZIO[Ctx, Error, ?]]",
    implicit tc => ApplicativeAskTests[ZIO[Ctx, Error, ?], Ctx].applicativeAsk[Ctx]
  )

  checkAllAsync(
    "ApplicativeLocal[ZIO[Ctx, Error, ?]]",
    implicit tc => ApplicativeLocalTests[ZIO[Ctx, Error, ?], Ctx].applicativeLocal[Ctx, Int]
  )

  checkAllAsync(
    "FunctorRaise[ZIO[Ctx, Error, ?]]",
    implicit tc => FunctorRaiseTests[ZIO[Ctx, Error, ?], Error].functorRaise[Int]
  )

  checkAllAsync(
    "ApplicativeHandle[ZIO[Ctx, Error, ?]]",
    implicit tc => ApplicativeHandleTests[ZIO[Ctx, Error, ?], Error].applicativeHandle[Int]
  )

  def askSummoner[R, E]    = ApplicativeAsk[ZIO[R, E, ?], R]
  def localSummoner[R, E]  = ApplicativeLocal[ZIO[R, E, ?], R]
  def raiseSummoner[R, E]  = FunctorRaise[ZIO[R, E, ?], E]
  def handleSummoner[R, E] = ApplicativeHandle[ZIO[R, E, ?], E]
}
