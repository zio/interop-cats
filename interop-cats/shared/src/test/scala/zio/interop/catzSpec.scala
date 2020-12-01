package zio.interop

import cats.arrow.ArrowChoice
import cats.effect.ParallelF
import cats.effect.laws._
import cats.effect.unsafe.IORuntime
import cats.implicits._
import cats.laws.discipline._
import zio._
import zio.clock.Clock
import zio.interop.catz._

import scala.concurrent.duration._

class catzSpec extends catzSpecZIOBase {
  checkAllAsync("Async[Task]", implicit tc => AsyncTests[RIO[Clock with CBlocking, *]].async[Int, Int, Int](1.second))
  checkAllAsync("GenSpawn[IO[Int, *]]", implicit tc => GenSpawnTests[IO[Int, *], Int].spawn[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, *]]", implicit tc => MonadErrorTests[IO[Int, *], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, *]]", implicit tc => MonoidKTests[IO[Int, *]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], *]]", implicit tc => SemigroupKTests[IO[Option[Unit], *]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, ParallelF[Task, *]].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", implicit tc => MonadTests[UIO].apply[Int, Int, Int])
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

  object summoningInstancesTest {
    import cats._
    import cats.effect._

    Concurrent[RIO[String, *]]
    Async[RIO[zio.clock.Clock with CBlocking, *]]
    Sync[RIO[zio.clock.Clock with CBlocking, *]]
    MonadError[RIO[String, *], Throwable]
    Monad[RIO[String, *]]
    Applicative[RIO[String, *]]
    Functor[RIO[String, *]]
    Parallel[RIO[String, *], ParallelF[RIO[String, *], *]]
    SemigroupK[RIO[String, *]]
    implicitly[Parallel[RIO[String, *]]]
    Apply[UIO]
    MonadError[ZManaged[String, Throwable, *], Throwable]
    Monad[ZManaged[String, Throwable, *]]
    Applicative[ZManaged[String, Throwable, *]]
    Functor[ZManaged[String, Throwable, *]]
    SemigroupK[ZManaged[String, Throwable, *]]

    def liftRIO(implicit runtime: IORuntime)      = LiftIO[RIO[String, *]]
    def liftZManaged(implicit runtime: IORuntime) = LiftIO[ZManaged[String, Throwable, *]]

    // related to issue #173
    def getArrow[F[-_, +_, +_], R, E, A](f: F[R, E, A])(implicit a: ArrowChoice[F[*, E, *]]): Any = (a, f)
    getArrow(ZIO.environment[Int])
    getArrow(ZManaged.environment[Int])
  }

  object syntaxTest {
    def rioDimap(rio: RIO[Int, String]): RIO[String, Int]      = rio.dimap[String, Int](_.length)(_.length)
    def rioBimap(rio: RIO[Int, String]): ZIO[Int, String, Int] = rio.bimap(_.getMessage, _.length)
    def urioDimap(rio: URIO[Int, String]): URIO[String, Int]   = rio.dimap[String, Int](_.length)(_.length)
  }
}
