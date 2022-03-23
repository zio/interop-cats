package zio.interop.test

import cats.data.NonEmptyList
import cats.{ Bifunctor, Monad, MonadError, SemigroupK }
import zio.*
import zio.interop.catz.core.*
import zio.managed.*
import zio.stream.interop.catz.core.*
import zio.stream.{ Stream, ZStream }
import zio.test.*

object CoreSummonSpec extends DefaultRunnableSpec {
  override def spec =
    suite("summons from catz.core work with only a cats-core dependency")(
      test("ZIO instances") {
        val monad      = implicitly[Monad[UIO]]
        val monadError = implicitly[MonadError[Task, Throwable]]
        val semigroupK = implicitly[SemigroupK[IO[NonEmptyList[Unit], _]]]
        val bifunctor  = implicitly[Bifunctor[IO]]

        monad.map(ZIO.unit)(identity)
        monadError.map(ZIO.unit)(identity)
        semigroupK.combineK(ZIO.unit, ZIO.unit)
        bifunctor.leftMap(ZIO.fromOption(None))(identity)

        assertCompletes
      },
      test("ZManaged instances") {
        val monad      = implicitly[Monad[ZManaged[Any, Nothing, _]]]
        val monadError = implicitly[MonadError[Managed[Throwable, _], Throwable]]
        val semigroupK = implicitly[SemigroupK[Managed[Nothing, _]]]
        val bifunctor  = implicitly[Bifunctor[Managed]]

        monad.map(ZManaged.unit)(identity)
        monadError.map(ZManaged.unit)(identity)
        semigroupK.combineK(ZManaged.unit, ZManaged.unit)
        bifunctor.leftMap(ZManaged.fail(()))(identity)

        assertCompletes
      },
      test("ZStream instances") {
        val monad      = implicitly[Monad[ZStream[Any, Nothing, _]]]
        val monadError = implicitly[MonadError[Stream[Throwable, _], Throwable]]
        val semigroupK = implicitly[SemigroupK[Stream[Nothing, _]]]
        val bifunctor  = implicitly[Bifunctor[Stream]]

        monad.map(ZStream.unit)(identity)
        monadError.map(ZStream.unit)(identity)
        semigroupK.combineK(ZStream.unit, ZStream.unit)
        bifunctor.leftMap(ZStream.fail(()))(identity)

        assertCompletes
      }
    )
}
