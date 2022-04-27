package zio.interop

import cats.Monad
import cats.effect.concurrent.Deferred
import cats.effect.laws._
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests }
import cats.effect.{ Concurrent, ConcurrentEffect, ContextShift, Effect }
import cats.implicits._
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{ Arbitrary, Gen }
import zio._
import zio.interop.catz._

class catzSpec extends catzSpecZIOBase {

  def genUIO[A: Arbitrary]: Gen[UIO[A]] =
    Gen.oneOf(genSuccess[Nothing, A], genIdentityTrans(genSuccess[Nothing, A]))

  checkAllAsync(
    "ConcurrentEffect[Task]",
    implicit tc => ConcurrentEffectTestsOverrides[Task].concurrentEffect[Int, Int, Int]
  )
  checkAllAsync("Effect[Task]", implicit tc => EffectTestsOverrides[Task].effect[Int, Int, Int])
  checkAllAsync("Concurrent[Task]", implicit tc => ConcurrentTestsOverrides[Task].concurrent[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, *]]", implicit tc => MonadErrorTests[IO[Int, *], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, *]]", implicit tc => MonoidKTests[IO[Int, *]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], *]]", implicit tc => SemigroupKTests[IO[Option[Unit], *]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, ParIO[Any, Throwable, *]].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", { implicit tc =>
    implicit def ioArbitrary[A: Arbitrary]: Arbitrary[UIO[A]] = Arbitrary(genUIO[A])
    MonadTests[UIO].apply[Int, Int, Int]
  })

  object summoningInstancesTest {
    import cats._
    import cats.effect._

    Concurrent[RIO[String, *]]
    Async[RIO[String, *]]
    LiftIO[RIO[String, *]]
    Sync[RIO[String, *]]
    MonadError[RIO[String, *], Throwable]
    Monad[RIO[String, *]]
    Applicative[RIO[String, *]]
    Functor[RIO[String, *]]
    Parallel[RIO[String, *], ParIO[String, Throwable, *]]
    SemigroupK[RIO[String, *]]
    implicitly[Parallel[RIO[String, *]]]
    Apply[UIO]

    def concurrentEffect[R: Runtime] = ConcurrentEffect[RIO[R, *]]
    def effect[R: Runtime]           = Effect[RIO[R, *]]
  }

  object summoningRuntimeInstancesTest {
    import cats.effect._
    import zio.interop.catz.implicits._

    ContextShift[Task]
    ContextShift[RIO[String, *]]
    cats.effect.Clock[Task]
    Timer[Task]

    ContextShift[UIO]
    cats.effect.Clock[UIO]
    Timer[UIO]
    Monad[UIO]
  }

  test("catsCoreSummoningTest") {
    import catz._
    import catz.core.{ deferInstance => deferI }
    // check that core deferInstance does not conflict with taskConcurrentInstance
    val _     = deferI // unused import
    val defer = cats.Defer[Task]
    val sync  = cats.effect.Sync[Task]
    assert(defer eq sync)
  }

  object concurrentEffectSyntaxTest {
    import cats.effect.syntax.all._

    ZIO.concurrentEffectWith { implicit CE: ConcurrentEffect[Task] =>
      ZIO
        .attempt(List(1, 2).parTraverseN[Task, Unit](5L) { _ =>
          ZIO.unit
        })
        .start
    }
  }

  object syntaxTest {
    def rioBimap(rio: RIO[Int, String]): ZIO[Int, String, Int] = rio.mapBoth(_.getMessage, _.length)
  }
}

trait AsyncLawsOverrides[F[_]] extends AsyncLaws[F] {
  import cats.effect.ExitCase.{ Completed, Error }

  override def bracketReleaseIsCalledOnCompletedOrError[A, B](fa: F[A], b: B) = {
    val lh = Deferred.uncancelable[F, B].flatMap { promise =>
      val br = F.bracketCase(F.delay(promise)) { _ =>
        fa
      } {
        case (r, Completed | Error(_)) => r.complete(b)
        case _                         => F.unit
      }
      // Start and forget
      // we attempt br because even if fa fails, we expect the release function
      // to run and set the promise.
      F.asyncF[Unit](cb => F.delay(cb(Right(()))) *> br.attempt.as(())) *> promise.get
    }
    lh <-> fa.attempt.as(b)
  }

}

object EffectTestsOverrides {

  def apply[F[_]](implicit ev: Effect[F]): EffectTests[F] =
    new EffectTests[F] {
      def laws: EffectLaws[F] = new EffectLaws[F] with AsyncLawsOverrides[F] {
        override val F: Effect[F] = ev
      }
    }
}

object ConcurrentTestsOverrides {

  def apply[F[_]](implicit ev: Concurrent[F], cs: ContextShift[F]): ConcurrentTests[F] =
    new ConcurrentTests[F] {
      def laws: ConcurrentLaws[F] =
        new ConcurrentLaws[F] with AsyncLawsOverrides[F] {
          override val F: Concurrent[F]              = ev
          override val contextShift: ContextShift[F] = cs
        }
    }
}

object ConcurrentEffectTestsOverrides {

  def apply[F[_]](implicit ev: ConcurrentEffect[F], cs: ContextShift[F]): ConcurrentEffectTests[F] =
    new ConcurrentEffectTests[F] {
      def laws: ConcurrentEffectLaws[F] =
        new ConcurrentEffectLaws[F] with AsyncLawsOverrides[F] {
          override val F: ConcurrentEffect[F]        = ev
          override val contextShift: ContextShift[F] = cs
        }
    }
}
