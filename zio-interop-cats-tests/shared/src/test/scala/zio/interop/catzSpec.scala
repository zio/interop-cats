package zio.interop

import cats.Monad
import cats.arrow.ArrowChoice
import cats.effect.concurrent.Deferred
import cats.effect.laws._
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests, SyncTests }
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
  checkAllAsync(
    "ArrowChoice[ZIO]",
    implicit tc => ArrowChoiceTests[ZIO[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync("Contravariant[ZIO]", implicit tc => ContravariantTests[ZIO[*, Int, Int]].contravariant[Int, Int, Int])

  // ZManaged Tests
  checkAllAsync("Monad[ZManaged]", implicit tc => MonadTests[ZManaged[Any, Throwable, *]].apply[Int, Int, Int])
  checkAllAsync("Monad[ZManaged]", implicit tc => ExtraMonadTests[ZManaged[Any, Throwable, *]].monadExtras[Int])
  checkAllAsync("SemigroupK[ZManaged]", implicit tc => SemigroupKTests[ZManaged[Any, Throwable, *]].semigroupK[Int])
  checkAllAsync(
    "ArrowChoice[ZManaged]",
    implicit tc => ArrowChoiceTests[ZManaged[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync(
    "MonadError[ZManaged]",
    implicit tc => MonadErrorTests[ZManaged[Any, Int, *], Int].monadError[Int, Int, Int]
  )
  checkAllAsync("Sync[ZManaged]", implicit tc => SyncTests[ZManaged[Any, Throwable, *]].sync[Int, Int, Int])

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
    LiftIO[ZManaged[String, Throwable, *]]
    MonadError[ZManaged[String, Throwable, *], Throwable]
    Monad[ZManaged[String, Throwable, *]]
    Applicative[ZManaged[String, Throwable, *]]
    Functor[ZManaged[String, Throwable, *]]
    SemigroupK[ZManaged[String, Throwable, *]]
    Sync[ZManaged[String, Throwable, *]]

    def concurrentEffect[R: Runtime] = ConcurrentEffect[RIO[R, *]]
    def effect[R: Runtime]           = Effect[RIO[R, *]]

    // related to issue #173
    def getArrow[F[-_, +_, +_], R, E, A](f: F[R, E, A])(implicit a: ArrowChoice[F[*, E, *]]): Any = (a, f)
    getArrow(ZIO.environment[Int])
    getArrow(ZManaged.environment[Int])
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

    Task.concurrentEffectWith { implicit CE =>
      Task(List(1, 2).parTraverseN(5L) { _ =>
        Task(())
      }).start
    }
  }

  object syntaxTest {
    def rioDimap(rio: RIO[Int, String]): RIO[String, Int]      = rio.dimap[String, Int](_.length)(_.length)
    def rioBimap(rio: RIO[Int, String]): ZIO[Int, String, Int] = rio.mapBoth(_.getMessage, _.length)
    def urioDimap(rio: URIO[Int, String]): URIO[String, Int]   = rio.dimap[String, Int](_.length)(_.length)
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
