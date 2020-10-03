package zio.stream.interop

import cats.implicits._
import cats.laws.discipline._
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.stream._
import zio.stream.interop.catz._

class catzSpec extends catzSpecZStreamBase with GenStreamInteropCats {

  def genUStream[A: Arbitrary]: Gen[Stream[Nothing, A]] =
    Gen.oneOf(genSuccess[Nothing, A], genIdentityTrans(genSuccess[Nothing, A]))

  checkAllAsync(
    "MonadError[Stream[Int, ?]]",
    implicit tc => MonadErrorTests[Stream[Int, ?], Int].monadError[Int, Int, Int]
  )
  checkAllAsync(
    "Parallel[Stream[Throwable, ?]]",
    implicit tc => ParallelTests[Stream[Throwable, ?], ParStream[Any, Throwable, ?]].parallel[Int, Int]
  )
  checkAllAsync("MonoidK[Stream[Int, ?]]", implicit tc => MonoidKTests[Stream[Int, ?]].monoidK[Int])
  checkAllAsync(
    "SemigroupK[Stream[Option[Unit], ?]]",
    implicit tc => SemigroupKTests[Stream[Option[Unit], ?]].semigroupK[Int]
  )
  checkAllAsync(
    "SemigroupK[Stream[Throwable, ?]]",
    implicit tc => SemigroupKTests[Stream[Throwable, ?]].semigroupK[Int]
  )
  checkAllAsync("Bifunctor[Stream]", implicit tc => BifunctorTests[Stream].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync(
    "Monad[Stream[Nothing, ?]]", { implicit tc =>
      implicit def streamArbitrary[A: Arbitrary: Cogen]: Arbitrary[Stream[Nothing, A]] = Arbitrary(genUStream[A])
      MonadTests[Stream[Nothing, ?]].apply[Int, Int, Int]
    }
  )
  checkAllAsync(
    "ArrowChoice[ZStream]",
    implicit tc => ArrowChoiceTests[ZStream[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats._

    Alternative[ZStream[String, Throwable, ?]]
    MonadError[ZStream[String, Throwable, ?], Throwable]
    Monad[ZStream[String, Throwable, ?]]
    Applicative[ZStream[String, Throwable, ?]]
    Functor[ZStream[String, Throwable, ?]]
    SemigroupK[ZStream[String, Throwable, ?]]
    Apply[ZStream[Any, Nothing, ?]]
  }
}
