package zio.interop

import cats.Eq
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests }
import cats.effect.laws.util.{ TestContext, TestInstances }
import cats.implicits._
import cats.laws.discipline._
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
import zio.random.Random
import zio.system.System

class catzSpec
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with GeneratorDrivenPropertyChecks
    with Discipline
    with TestInstances
    with GenIO {

  type Env = Clock with Console with System with Random

  implicit def rts(implicit tc: TestContext): Runtime[Env] = new DefaultRuntime {
    override val Platform = PlatformLive.fromExecutionContext(tc).withReportFailure(_ => ())
  }

  implicit val zioEqNoCause: Eq[Cause[Nothing]] =
    Eq.fromUniversalEquals

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

  def genUIO[A: Arbitrary]: Gen[UIO[A]] =
    Gen.oneOf(genSuccess[Nothing, A], genIdentityTrans(genSuccess[Nothing, A]))

  def checkAllAsync(name: String, f: TestContext => Laws#RuleSet): Unit =
    checkAll(name, f(TestContext()))

  // TODO: reintroduce repeated ConcurrentTests as they're removed due to the hanging CI builds (see https://github.com/scalaz/scalaz-zio/pull/482)
  checkAllAsync("ConcurrentEffect[Task]", implicit tc => ConcurrentEffectTests[Task].concurrentEffect[Int, Int, Int])
  checkAllAsync("Effect[Task]", implicit tc => EffectTests[Task].effect[Int, Int, Int])
  checkAllAsync("Concurrent[Task]", implicit tc => ConcurrentTests[Task].concurrent[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, ?]]", implicit tc => MonadErrorTests[IO[Int, ?], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, ?]]", implicit tc => MonoidKTests[IO[Int, ?]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], ?]]", implicit tc => SemigroupKTests[IO[Option[Unit], ?]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task, Task.Par]", implicit tc => ParallelTests[Task, Util.Par].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", { implicit tc =>
    implicit def ioArbitrary[A: Arbitrary: Cogen]: Arbitrary[UIO[A]] = Arbitrary(genUIO[A])
    MonadTests[UIO].apply[Int, Int, Int]
  })

  // ZManaged Tests
  checkAllAsync("Monad[ZManaged]", implicit tc => MonadTests[ZManaged[Any, Throwable, ?]].apply[Int, Int, Int])
  checkAllAsync("Monad[ZManaged]", implicit tc => ExtraMonadTests[ZManaged[Any, Throwable, ?]].monadExtras[Int])
  checkAllAsync("SemigroupK[ZManaged]", implicit tc => SemigroupKTests[ZManaged[Any, Throwable, ?]].semigroupK[Int])
  checkAllAsync(
    "MonadError[ZManaged]",
    implicit tc => MonadErrorTests[ZManaged[Any, Int, ?], Int].monadError[Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats._
    import cats.effect._

    Concurrent[RIO[String, ?]]
    Async[RIO[String, ?]]
    LiftIO[RIO[String, ?]]
    Sync[RIO[String, ?]]
    MonadError[RIO[String, ?], Throwable]
    Monad[RIO[String, ?]]
    Applicative[RIO[String, ?]]
    Functor[RIO[String, ?]]
    Parallel[RIO[String, ?], ParIO[String, Throwable, ?]]
    SemigroupK[RIO[String, ?]]
    Apply[UIO]
    LiftIO[ZManaged[String, Throwable, ?]]
    MonadError[ZManaged[String, Throwable, ?], Throwable]
    Monad[ZManaged[String, Throwable, ?]]
    Applicative[ZManaged[String, Throwable, ?]]
    Functor[ZManaged[String, Throwable, ?]]
    SemigroupK[ZManaged[String, Throwable, ?]]

    def concurrentEffect[R: Runtime] = ConcurrentEffect[RIO[R, ?]]
    def effect[R: Runtime]           = Effect[RIO[R, ?]]
  }

  object summoningRuntimeInstancesTest {
    import cats.effect._
    import zio.interop.catz.implicits._

    ContextShift[Task]
    ContextShift[RIO[String, ?]]
    cats.effect.Clock[Task]
    Timer[Task]

    ContextShift[UIO]
    cats.effect.Clock[UIO]
    Timer[UIO]
  }
}
