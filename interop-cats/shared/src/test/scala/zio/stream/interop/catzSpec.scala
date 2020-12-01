package zio.stream.interop

import cats.effect.ParallelF
import cats.implicits._
import cats.laws.discipline._
import zio.stream._
import zio.stream.interop.catz._

class catzSpec extends catzSpecZStreamBase with GenStreamInteropCats {

  checkAllAsync(
    "MonadError[Stream[Int, *]]",
    implicit tc => MonadErrorTests[Stream[Int, *], Int].monadError[Int, Int, Int]
  )
  checkAllAsync(
    "Parallel[Stream[Throwable, *]]",
    implicit tc => ParallelTests[Stream[Throwable, *], ParallelF[Stream[Throwable, *], *]].parallel[Int, Int]
  )
  checkAllAsync("MonoidK[Stream[Int, *]]", implicit tc => MonoidKTests[Stream[Int, *]].monoidK[Int])
  checkAllAsync(
    "SemigroupK[Stream[Option[Unit], *]]",
    implicit tc => SemigroupKTests[Stream[Option[Unit], *]].semigroupK[Int]
  )
  checkAllAsync(
    "SemigroupK[Stream[Throwable, *]]",
    implicit tc => SemigroupKTests[Stream[Throwable, *]].semigroupK[Int]
  )
  checkAllAsync("Bifunctor[Stream]", implicit tc => BifunctorTests[Stream].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Monad[UStream]", implicit tc => MonadTests[UStream].apply[Int, Int, Int])
  checkAllAsync(
    "ArrowChoice[ZStream]",
    implicit tc => ArrowChoiceTests[ZStream[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats._

    Alternative[ZStream[String, Throwable, *]]
    MonadError[ZStream[String, Throwable, *], Throwable]
    Monad[ZStream[String, Throwable, *]]
    Applicative[ZStream[String, Throwable, *]]
    Functor[ZStream[String, Throwable, *]]
    SemigroupK[ZStream[String, Throwable, *]]
    Apply[ZStream[Any, Nothing, *]]
  }
}
