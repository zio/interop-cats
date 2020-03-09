package zio.interop

import cats.effect.concurrent.Deferred
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline.{ ConcurrentEffectTests, ConcurrentTests, EffectTests, SyncTests }
import cats.effect.laws._
import cats.effect.{ Concurrent, ConcurrentEffect, ContextShift, Effect }
import cats.implicits._
import cats.laws._
import cats.laws.discipline._
import cats.{ Monad, SemigroupK }
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.interop.catz._
import zio.{ IO, _ }

class catzSpec extends catzSpecZIOBase {

  def genUIO[A: Arbitrary]: Gen[UIO[A]] =
    Gen.oneOf(genSuccess[Nothing, A], genIdentityTrans(genSuccess[Nothing, A]))

  for (i <- 1 to 10)
    checkAllAsync(
      s"ConcurrentEffect[Task] $i",
      implicit tc => ConcurrentEffectTestsOverrides[Task].concurrentEffect[Int, Int, Int]
    )
  checkAllAsync("Effect[Task]", implicit tc => EffectTestsOverrides[Task].effect[Int, Int, Int])
  checkAllAsync("Concurrent[Task]", implicit tc => ConcurrentTestsOverrides[Task].concurrent[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, *]]", implicit tc => MonadErrorTests[IO[Int, *], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, *]]", implicit tc => MonoidKTests[IO[Int, *]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], *]]", implicit tc => SemigroupKTests[IO[Option[Unit], *]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTestsOverrides[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, ParIO[Any, Throwable, *]].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", { implicit tc =>
    implicit def ioArbitrary[A: Arbitrary: Cogen]: Arbitrary[UIO[A]] = Arbitrary(genUIO[A])
    MonadTests[UIO].apply[Int, Int, Int]
  })
  checkAllAsync(
    "ArrowChoice[ZIO]",
    implicit tc => ArrowChoiceTests[ZIO[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )

  // ZManaged Tests
  checkAllAsync("Monad[ZManaged]", implicit tc => MonadTests[ZManaged[Any, Throwable, *]].apply[Int, Int, Int])
  checkAllAsync("Monad[ZManaged]", implicit tc => ExtraMonadTests[ZManaged[Any, Throwable, *]].monadExtras[Int])
  checkAllAsync("SemigroupK[ZManaged]", implicit tc => SemigroupKTests[ZManaged[Any, Throwable, *]].semigroupK[Int])
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
    def rioBimap(rio: RIO[Int, String]): ZIO[Int, String, Int] = rio.bimap(_.getMessage, _.length)
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

trait BracketLawsOverrides[F[_], E] extends BracketLaws[F, E] {}

trait ConcurrentLawsOverrides[F[_]] extends ConcurrentLaws[F] {}

trait SemigroupKLawsOverrides[F[_]] extends SemigroupKLaws[F] {}

trait ConcurrentEffectLawsOverrides[F[_]] extends ConcurrentEffectLaws[F] {}

object SemigroupKTestsOverrides {

  def apply[F[_]](implicit ev: SemigroupK[F]): SemigroupKTests[F] =
    new SemigroupKTests[F] {
      def laws: SemigroupKLaws[F] = new SemigroupKLawsOverrides[F] {
        override implicit def F: SemigroupK[F] = ev
      }
    }

}

object EffectTestsOverrides {

  def apply[F[_]](implicit ev: Effect[F]): EffectTests[F] =
    new EffectTests[F] {
      def laws: EffectLaws[F] = new EffectLaws[F] with AsyncLawsOverrides[F] with BracketLawsOverrides[F, Throwable] {
        override val F: Effect[F] = ev
      }
    }
}

object ConcurrentTestsOverrides {

  def apply[F[_]](implicit ev: Concurrent[F], cs: ContextShift[F]): ConcurrentTests[F] =
    new ConcurrentTests[F] {
      def laws: ConcurrentLaws[F] =
        new ConcurrentLawsOverrides[F] with AsyncLawsOverrides[F] with BracketLawsOverrides[F, Throwable] {
          override val F: Concurrent[F]              = ev
          override val contextShift: ContextShift[F] = cs
        }
    }
}

object ConcurrentEffectTestsOverrides {

  def apply[F[_]](implicit ev: ConcurrentEffect[F], cs: ContextShift[F]): ConcurrentEffectTests[F] =
    new ConcurrentEffectTests[F] {
      def laws: ConcurrentEffectLaws[F] =
        new ConcurrentEffectLawsOverrides[F]
          with AsyncLawsOverrides[F]
          with BracketLawsOverrides[F, Throwable]
          with ConcurrentLawsOverrides[F] {
          override val F: ConcurrentEffect[F]        = ev
          override val contextShift: ContextShift[F] = cs
        }
    }
}
