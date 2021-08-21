package zio.stream.interop

import cats.effect.ParallelF
import cats.implicits._
import cats.laws.discipline._
import zio.stream._
import zio.stream.interop.catz._

class ZStreamSpec extends ZStreamSpecBase with GenStreamInteropCats {

  checkAllAsync(
    "MonadError[Stream[Int, _]]",
    implicit tc => MonadErrorTests[Stream[Int, _], Int].monadError[Int, Int, Int]
  )
  checkAllAsync(
    "Parallel[Stream[Throwable, _]]",
    implicit tc => ParallelTests[Stream[Throwable, _], ParallelF[Stream[Throwable, _], _]].parallel[Int, Int]
  )
  checkAllAsync("MonoidK[Stream[Int, _]]", implicit tc => MonoidKTests[Stream[Int, _]].monoidK[Int])
  checkAllAsync(
    "SemigroupK[Stream[Option[Unit], _]]",
    implicit tc => SemigroupKTests[Stream[Option[Unit], _]].semigroupK[Int]
  )
  checkAllAsync(
    "SemigroupK[Stream[Throwable, _]]",
    implicit tc => SemigroupKTests[Stream[Throwable, _]].semigroupK[Int]
  )
  checkAllAsync("Bifunctor[Stream]", implicit tc => BifunctorTests[Stream].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Monad[UStream]", implicit tc => MonadTests[UStream].apply[Int, Int, Int])
  checkAllAsync(
    "ArrowChoice[ZStream]",
    implicit tc => ArrowChoiceTests[ZStream[_, Int, _]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats._

    Alternative[ZStream[String, Throwable, _]]
    MonadError[ZStream[String, Throwable, _], Throwable]
    Monad[ZStream[String, Throwable, _]]
    Applicative[ZStream[String, Throwable, _]]
    Functor[ZStream[String, Throwable, _]]
    SemigroupK[ZStream[String, Throwable, _]]
    Apply[ZStream[Any, Nothing, _]]
  }
}
