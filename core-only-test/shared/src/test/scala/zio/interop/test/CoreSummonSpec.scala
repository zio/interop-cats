package zio.interop.test

import cats.data.NonEmptyList
import cats.{ Bifunctor, Monad, MonadError, SemigroupK }
import zio._
import zio.interop.catz.core._
import zio.stream.interop.catz.core._
import zio.stream.{ Stream, ZStream }
import zio.test.{ DefaultRunnableSpec, _ }

object CoreSummonSpec extends DefaultRunnableSpec {
  override def spec =
    suite("summons from catz.core work with only a cats-core dependency")(
      test("ZIO instances") {
        val monad      = implicitly[Monad[UIO]]
        val monadError = implicitly[MonadError[Task, Throwable]]
        val semigroupK = implicitly[SemigroupK[IO[NonEmptyList[Unit], *]]]
        val bifunctor  = implicitly[Bifunctor[IO]]

        monad.map(ZIO.unit)(identity)
        monadError.map(ZIO.unit)(identity)
        semigroupK.combineK(ZIO.unit, ZIO.unit)
        bifunctor.leftMap(ZIO.fromOption(None))(identity)

        assertCompletes
      },
      test("ZManaged instances") {
        val monad      = implicitly[Monad[ZManaged[Any, Nothing, *]]]
        val monadError = implicitly[MonadError[Managed[Throwable, *], Throwable]]
        val semigroupK = implicitly[SemigroupK[Managed[Nothing, *]]]
        val bifunctor  = implicitly[Bifunctor[Managed]]

        monad.map(ZManaged.unit)(identity)
        monadError.map(ZManaged.unit)(identity)
        semigroupK.combineK(ZManaged.unit, ZManaged.unit)
        bifunctor.leftMap(ZManaged.fail(()))(identity)

        assertCompletes
      },
      test("ZStream instances") {
        val monad      = implicitly[Monad[ZStream[Any, Nothing, *]]]
        val monadError = implicitly[MonadError[Stream[Throwable, *], Throwable]]
        val semigroupK = implicitly[SemigroupK[Stream[Nothing, *]]]
        val bifunctor  = implicitly[Bifunctor[Stream]]

        monad.map(ZStream.unit)(identity)
        monadError.map(ZStream.unit)(identity)
        semigroupK.combineK(ZStream.unit, ZStream.unit)
        bifunctor.leftMap(ZStream.fail(()))(identity)

        assertCompletes
      }
    )
}
