package zio.interop

import cats.Eq
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import org.scalacheck.{ Arbitrary }
import org.scalatest.funsuite.AnyFunSuite
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.Discipline
import zio.clock.Clock
import zio.console.Console
import zio.internal.{ Executor, PlatformLive }
import zio.interop.catz.taskEffectInstance
import zio.random.Random
import zio.system.System
import zio.{ Cause, DefaultRuntime, IO, Runtime, UIO, ZIO }

private[zio] trait catzSpecBase extends AnyFunSuite with Discipline with TestInstances with catzSpecBaseLowPriority {

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

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext()))

}

private[interop] sealed trait catzSpecBaseLowPriority { this: catzSpecBase =>

  implicit def zioEq[R: Arbitrary, E, A: Eq](implicit tc: TestContext): Eq[ZIO[R, E, A]] = {
    def run(r: R, zio: ZIO[R, E, A]) = taskEffectInstance.toIO(zio.provide(r).sandbox.either)
    Eq.instance((io1, io2) => Arbitrary.arbitrary[R].sample.fold(false)(r => catsSyntaxEq(run(r, io1)) eqv run(r, io2)))
  }

}
