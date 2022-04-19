package zio.interop

import cats.Eq
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import org.scalacheck.{ Arbitrary, Cogen }
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import zio.interop.catz.taskEffectInstance
import zio.{
  Cause,
  Clock,
  Console,
  Executor,
  IO,
  Random,
  Runtime,
  RuntimeConfig,
  System,
  Tag,
  Task,
  UIO,
  ZEnvironment,
  ZIO
}

private[zio] trait catzSpecBase
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Configuration
    with TestInstances
    with catzSpecBaseLowPriority {

  type Env = Clock with Console with System with Random

  implicit def rts(implicit tc: TestContext): Runtime[Any] = Runtime(
    ZEnvironment.empty,
    RuntimeConfig
      .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(tc))
  )

  implicit val zioEqCauseNothing: Eq[Cause[Nothing]] = Eq.fromUniversalEquals

  implicit def zioEqIO[E: Eq, A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def zioEqTask[A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[Task[A]] =
    Eq.by(_.either)

  implicit def zioEqUIO[A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[UIO[A]] =
    Eq.by(uio => taskEffectInstance.toIO(uio.sandbox.either))

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext()))

}

private[interop] sealed trait catzSpecBaseLowPriority { this: catzSpecBase =>

  implicit def zioEq[R: Arbitrary: Tag, E: Eq, A: Eq](
    implicit rts: Runtime[Any],
    tc: TestContext
  ): Eq[ZIO[R, E, A]] = {
    def run(r: ZEnvironment[R], zio: ZIO[R, E, A]) = taskEffectInstance.toIO(zio.provideEnvironment(r).either)
    Eq.instance(
      (io1, io2) =>
        Arbitrary.arbitrary[ZEnvironment[R]].sample.fold(false)(r => catsSyntaxEq(run(r, io1)) eqv run(r, io2))
    )
  }

  implicit def zEnvironmentCogen[R: Cogen: Tag]: Cogen[ZEnvironment[R]] =
    Cogen[R].contramap(_.get)

  implicit def zEnvironmentArbitrary[R: Arbitrary: Tag]: Arbitrary[ZEnvironment[R]] =
    Arbitrary(Arbitrary.arbitrary[R].map(ZEnvironment(_)))
}
