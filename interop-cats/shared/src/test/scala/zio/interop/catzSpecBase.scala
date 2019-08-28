package zio.interop

import cats.Eq
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import org.scalatest.TestRegistration
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import zio.clock.Clock
import zio.console.Console
import zio.internal.{ Executor, PlatformLive }
import zio.interop.catz.taskEffectInstance
import zio.random.Random
import zio.system.System
import zio.{ Cause, DefaultRuntime, IO, Runtime, UIO, ZIO, ZManaged }

private[interop] trait catzSpecBase
    extends AnyFunSuite
    with GeneratorDrivenPropertyChecks
    with Discipline
    with TestInstances
    with GenIOInteropCats
    with catzSpecBaseLowPriority {

  type Env = Clock with Console with System with Random

  implicit def rts(implicit tc: TestContext): Runtime[Env] = new DefaultRuntime {
    override val Platform = PlatformLive
      .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(tc))
      .withReportFailure(_ => ())
  }

  implicit def zioEqCause[E]: Eq[Cause[E]] = zioEqCause0.asInstanceOf[Eq[Cause[E]]]
  private val zioEqCause0: Eq[Cause[Any]]  = Eq.fromUniversalEquals

  implicit def zioEqIO[E: Eq, A: Eq](implicit tc: TestContext): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def zioEqUIO[A: Eq](implicit tc: TestContext): Eq[UIO[A]] =
    Eq.by(uio => taskEffectInstance.toIO(uio.sandbox.either))

  implicit def zioEqParIO[E: Eq, A: Eq](implicit tc: TestContext): Eq[ParIO[Any, E, A]] =
    Eq.by(Par.unwrap(_))

  implicit def zioEqZManaged[E: Eq, A: Eq](implicit tc: TestContext): Eq[ZManaged[Any, E, A]] =
    Eq.by(_.reserve.flatMap(_.acquire).either)

  implicit def zioArbitrary[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[R => IO[E, A]].map(ZIO.environment[R].flatMap(_)))

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))

  implicit def ioParArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ParIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(Par.apply))

  implicit def zManagedArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(ZManaged.fromEffect(_)))

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext()))

}

private[interop] trait catzSpecBaseLowPriority { this: catzSpecBase =>

  implicit def zioEq[R: Arbitrary, E, A: Eq](implicit tc: TestContext): Eq[ZIO[R, E, A]] = {
    def run(r: R, zio: ZIO[R, E, A]) = taskEffectInstance.toIO(zio.provide(r).sandbox.either)
    Eq.instance((io1, io2) => Arbitrary.arbitrary[R].sample.fold(false)(r => catsSyntaxEq(run(r, io1)) eqv run(r, io2)))
  }

}
