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

class CatsSpec extends ZioSpecBase {

  // ZIO tests
  checkAllAsync(
    "Async[RIO[Clock & Blocking, *]]",
    implicit tc => AsyncTests[RIO[Clock & CBlocking, *]].async[Int, Int, Int](100.millis)
  )
  checkAllAsync(
    "Async[Task]", { implicit tc =>
      implicit val runtime: Runtime[Clock & CBlocking] = Runtime(environment, platform)
      AsyncTests[Task].async[Int, Int, Int](100.millis)
    }
  )
  checkAllAsync(
    "Temporal[RIO[Clock, *]]",
    implicit tc => GenTemporalTests[RIO[Clock, *], Throwable].temporal[Int, Int, Int](100.millis)
  )
  checkAllAsync(
    "Temporal[Task]", { implicit tc =>
      implicit val runtime: Runtime[Clock] = Runtime(environment, platform)
      GenTemporalTests[Task, Throwable].temporal[Int, Int, Int](100.millis)
    }
  )
  checkAllAsync("GenSpawn[IO[Int, *], Int]", implicit tc => GenSpawnTests[IO[Int, *], Int].spawn[Int, Int, Int])
  checkAllAsync("MonadError[IO[In t, *]]", implicit tc => MonadErrorTests[IO[Int, *], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, *]]", implicit tc => MonoidKTests[IO[Int, *]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], *]]", implicit tc => SemigroupKTests[IO[Option[Unit], *]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, ParallelF[Task, *]].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", implicit tc => MonadTests[UIO].apply[Int, Int, Int])
  checkAllAsync(
    "ArrowChoice[ZIO[*, Int, *]]",
    implicit tc => ArrowChoiceTests[ZIO[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync(
    "Contravariant[ZIO[*, Int, Int]]",
    implicit tc => ContravariantTests[ZIO[*, Int, Int]].contravariant[Int, Int, Int]
  )

  // ZManaged Tests
  checkAllAsync("Monad[TaskManaged]", implicit tc => MonadTests[TaskManaged].apply[Int, Int, Int])
  checkAllAsync("Monad[TaskManaged]", implicit tc => ExtraMonadTests[TaskManaged].monadExtras[Int])
  checkAllAsync("SemigroupK[TaskManaged]", implicit tc => SemigroupKTests[TaskManaged].semigroupK[Int])
  checkAllAsync(
    "ArrowChoice[ZManaged[*, Int, *]]",
    implicit tc => ArrowChoiceTests[ZManaged[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync(
    "MonadError[Managed[Int, *]]",
    implicit tc => MonadErrorTests[Managed[Int, *], Int].monadError[Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats._
    import cats.effect._
    import zio.clock.{ Clock => ZClock }

    Async[RIO[ZClock & CBlocking, *]]
    Sync[RIO[ZClock & CBlocking, *]]
    GenTemporal[ZIO[ZClock, Int, *], Int]
    Temporal[RIO[ZClock, *]]
    GenConcurrent[ZIO[String, Int, *], Int]
    Concurrent[RIO[String, *]]
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

    def liftRIO(implicit runtime: IORuntime)                  = LiftIO[RIO[String, *]]
    def liftZManaged(implicit runtime: IORuntime)             = LiftIO[ZManaged[String, Throwable, *]]
    def runtimeGenTemporal(implicit runtime: Runtime[ZClock]) = GenTemporal[ZIO[Any, Int, *], Int]
    def runtimeTemporal(implicit runtime: Runtime[ZClock])    = Temporal[Task]

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
