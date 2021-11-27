package zio.interop

import cats.arrow.ArrowChoice
import cats.effect.ParallelF
import cats.effect.laws.*
import cats.effect.unsafe.IORuntime
import cats.laws.discipline.*
import cats.syntax.all.*
import zio.*
import zio.clock.Clock
import zio.interop.catz.*

import scala.concurrent.duration.*

class CatsSpec extends ZioSpecBase {

  // ZIO tests
  checkAllAsync(
    "Async[RIO[Clock & Blocking, _]]",
    implicit tc => AsyncTests[RIO[Clock & CBlocking, _]].async[Int, Int, Int](100.millis)
  )
  checkAllAsync(
    "Async[Task]",
    { implicit tc =>
      implicit val runtime: Runtime[Clock & CBlocking] = Runtime(environment, platform)
      AsyncTests[Task].async[Int, Int, Int](100.millis)
    }
  )
  checkAllAsync(
    "Temporal[RIO[Clock, _]]",
    implicit tc => GenTemporalTests[RIO[Clock, _], Throwable].temporal[Int, Int, Int](100.millis)
  )
  checkAllAsync(
    "Temporal[Task]",
    { implicit tc =>
      implicit val runtime: Runtime[Clock] = Runtime(environment, platform)
      GenTemporalTests[Task, Throwable].temporal[Int, Int, Int](100.millis)
    }
  )
  checkAllAsync("GenSpawn[IO[Int, _], Int]", implicit tc => GenSpawnTests[IO[Int, _], Int].spawn[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, _]]", implicit tc => MonadErrorTests[IO[Int, _], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, _]]", implicit tc => MonoidKTests[IO[Int, _]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], _]]", implicit tc => SemigroupKTests[IO[Option[Unit], _]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, ParallelF[Task, _]].parallel[Int, Int])
  checkAllAsync("Monad[URIO[Int, _]]", implicit tc => MonadTests[URIO[Int, _]].monad[Int, Int, Int])
  checkAllAsync("Monad[URIO[Int, _]]", implicit tc => ExtraMonadTests[URIO[Int, _]].monadExtras[Int])
  checkAllAsync(
    "ArrowChoice[ZIO[_, Int, _]]",
    implicit tc => ArrowChoiceTests[ZIO[_, Int, _]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync(
    "Contravariant[ZIO[_, Int, Int]]",
    implicit tc => ContravariantTests[ZIO[_, Int, Int]].contravariant[Int, Int, Int]
  )

  // ZManaged Tests
  checkAllAsync("Monad[TaskManaged]", implicit tc => MonadTests[TaskManaged].apply[Int, Int, Int])
  checkAllAsync("Monad[TaskManaged]", implicit tc => ExtraMonadTests[TaskManaged].monadExtras[Int])
  checkAllAsync("SemigroupK[TaskManaged]", implicit tc => SemigroupKTests[TaskManaged].semigroupK[Int])
  checkAllAsync(
    "ArrowChoice[ZManaged[_, Int, _]]",
    implicit tc => ArrowChoiceTests[ZManaged[_, Int, _]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )
  checkAllAsync(
    "MonadError[Managed[Int, _]]",
    implicit tc => MonadErrorTests[Managed[Int, _], Int].monadError[Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats.*
    import cats.effect.*
    import zio.clock.Clock as ZClock

    Async[RIO[ZClock & CBlocking, _]]
    Sync[RIO[ZClock & CBlocking, _]]
    GenTemporal[ZIO[ZClock, Int, _], Int]
    Temporal[RIO[ZClock, _]]
    GenConcurrent[ZIO[String, Int, _], Int]
    Concurrent[RIO[String, _]]
    MonadError[RIO[String, _], Throwable]
    Monad[RIO[String, _]]
    Applicative[RIO[String, _]]
    Functor[RIO[String, _]]
    Parallel[RIO[String, _], ParallelF[RIO[String, _], _]]
    SemigroupK[RIO[String, _]]
    implicitly[Parallel[RIO[String, _]]]
    Apply[UIO]
    MonadError[ZManaged[String, Throwable, _], Throwable]
    Monad[ZManaged[String, Throwable, _]]
    Applicative[ZManaged[String, Throwable, _]]
    Functor[ZManaged[String, Throwable, _]]
    SemigroupK[ZManaged[String, Throwable, _]]

    def liftRIO(implicit runtime: IORuntime)                  = LiftIO[RIO[String, _]]
    def liftZManaged(implicit runtime: IORuntime)             = LiftIO[ZManaged[String, Throwable, _]]
    def runtimeGenTemporal(implicit runtime: Runtime[ZClock]) = GenTemporal[ZIO[Any, Int, _], Int]
    def runtimeTemporal(implicit runtime: Runtime[ZClock])    = Temporal[Task]

    // related to issue #173
    def getArrow[F[-_, +_, +_], R, E, A](f: F[R, E, A])(implicit a: ArrowChoice[F[_, E, _]]): Any = (a, f)
    getArrow(ZIO.environment[Int])
    getArrow(ZManaged.environment[Int])
  }

  object syntaxTest {
    def rioDimap(rio: RIO[Int, String]): RIO[String, Int]      = rio.dimap[String, Int](_.length)(_.length)
    def rioBimap(rio: RIO[Int, String]): ZIO[Int, String, Int] = rio.mapBoth(_.getMessage, _.length)
    def urioDimap(rio: URIO[Int, String]): URIO[String, Int]   = rio.dimap[String, Int](_.length)(_.length)
  }
}
