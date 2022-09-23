package zio.interop

import cats.effect.ParallelF
import cats.effect.laws.*
import cats.effect.unsafe.IORuntime
import cats.laws.discipline.*
import zio.interop.catz.*
import zio.managed.*
import zio.{ durationInt as _, _ }

import scala.concurrent.duration.*

class CatsSpec extends ZioSpecBase {

  // ZIO generic tests
  locally {
    import zio.interop.catz.generic.*
    type F[A] = IO[Int, A]
    type Err  = Cause[Int]
    checkAllAsync("Temporal[IO]", implicit tc => GenTemporalTests[F, Err].temporal[Int, Int, Int](100.millis))
    checkAllAsync("Spawn[IO]", implicit tc => GenSpawnTests[F, Cause[Int]].spawn[Int, Int, Int])
    checkAllAsync("MonadCancel[IO]", implicit tc => MonadCancelTests[F, Err].monadCancel[Int, Int, Int])
  }

  // ZIO tests
  checkAllAsync("Async[Task]", implicit tc => AsyncTests[Task].async[Int, Int, Int](100.millis))
  checkAllAsync("Temporal[Task]", implicit tc => GenTemporalTests[Task, Throwable].temporal[Int, Int, Int](100.millis))
  checkAllAsync("MonadError[IO]", implicit tc => MonadErrorTests[IO[Int, _], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO]", implicit tc => MonoidKTests[IO[Int, _]].monoidK[Int])
  checkAllAsync("SemigroupK[IO]", implicit tc => SemigroupKTests[IO[Option[Unit], _]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, ParallelF[Task, _]].parallel[Int, Int])
  checkAllAsync("Monad[URIO]", implicit tc => MonadTests[URIO[Int, _]].apply[Int, Int, Int])

  // ZManaged Tests
  checkAllAsync("Monad[TaskManaged]", implicit tc => MonadTests[TaskManaged].apply[Int, Int, Int])
  checkAllAsync("Monad[TaskManaged]", implicit tc => ExtraMonadTests[TaskManaged].monadExtras[Int])
  checkAllAsync("SemigroupK[TaskManaged]", implicit tc => SemigroupKTests[TaskManaged].semigroupK[Int])
  checkAllAsync("MonadError[Managed]", implicit tc => MonadErrorTests[Managed[Int, _], Int].monadError[Int, Int, Int])

  object summoningInstancesTest {
    import cats.*
    import cats.effect.*
    import zio.Clock as ZClock

    Async[RIO[ZClock, _]]
    Sync[RIO[ZClock, _]]
    Temporal[RIO[ZClock, _]]
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

    locally {
      import zio.interop.catz.generic.*
      GenTemporal[ZIO[ZClock, Int, _], Cause[Int]]
      GenConcurrent[ZIO[String, Int, _], Cause[Int]]
    }

    def liftRIO(implicit runtime: IORuntime)                  = LiftIO[RIO[String, _]]
    def liftZManaged(implicit runtime: IORuntime)             = LiftIO[ZManaged[String, Throwable, _]]
    def runtimeTemporal(implicit runtime: Runtime[ZClock])    = Temporal[Task]
    def runtimeGenTemporal(implicit runtime: Runtime[ZClock]) = {
      import zio.interop.catz.generic.*
      GenTemporal[ZIO[Any, Int, _], Cause[Int]]
    }
  }

  object syntaxTest {
    def rioBimap(rio: RIO[Int, String]): ZIO[Int, String, Int] = rio.mapBoth(_.getMessage, _.length)
  }
}
