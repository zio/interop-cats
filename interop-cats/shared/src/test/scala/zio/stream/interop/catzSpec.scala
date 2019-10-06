package zio.stream.interop

import cats.Monad
// import cats.effect.laws.discipline.arbitrary._
// import cats.implicits._
// import cats.laws._
import cats.instances.int._
import cats.laws.discipline.MonadErrorTests
// import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.stream.interop.catz._
import zio.stream._

class catzSpec extends catzSpecZStreamBase with GenStreamInteropCats {

  checkAllAsync(
    "MonadError[Stream[Int, ?]]",
    implicit tc => MonadErrorTests[Stream[Int, ?], Int].monadError[Int, Int, Int]
  )
  // checkAllAsync("MonoidK[Stream[Int, ?]]", implicit tc => MonoidKTests[Stream[Int, ?]].monoidK[Int])
  // checkAllAsync("SemigroupK[Stream[Option[Unit], ?]]", implicit tc => SemigroupKTests[Stream[Option[Unit], ?]].semigroupK[Int])
  // checkAllAsync("SemigroupK[Stream[Throwable, ?]]", implicit tc => SemigroupKTests[Stream[Throwable, ?]].semigroupK[Int])
  // checkAllAsync("Bifunctor[Stream]", implicit tc => BifunctorTests[Stream].bifunctor[Int, Int, Int, Int, Int, Int])
  // checkAllAsync("Monad[Stream[Nothing, ?]]", { implicit tc =>
  //   implicit def ioArbitrary[A: Arbitrary: Cogen]: Arbitrary[Stream[Nothing, A]] = Arbitrary(genUStream[A])
  //   MonadTests[Stream[Nothing, ?]].apply[Int, Int, Int]
  // })
  // checkAllAsync(
  //   "ArrowChoice[ZStream]",
  //   implicit tc => ArrowChoiceTests[ZStream[*, Int, *]].arrowChoice[Int, Int, Int, Int, Int, Int]
  // )

  object summoningInstancesTest {
    import cats._
    import cats.effect._

    MonadError[ZStream[String, Throwable, ?], Throwable]
    // Monad[ZStream[String, Throwable, ?]]
    // Applicative[ZStream[String, Throwable, ?]]
    // Functor[ZStream[String, Throwable, ?]]
    // SemigroupK[ZStream[String, Throwable, ?]]
    // Apply[ZStream[Any, Nothing, ?]]
  }

  object summoningRuntimeInstancesTest {
    import cats.effect._
    import zio.interop.catz.implicits._

    Monad[ZStream[Any, Nothing, ?]]
  }
}
