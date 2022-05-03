package zio.interop

import org.scalacheck.*
import zio.*
import zio.managed.*

/**
 * Temporary fork of zio.GenIO that overrides `genParallel` with ZManaged-based code
 * instead of `io.zipPar(parIo).map(_._1)`
 * because ZIP-PAR IS NON-DETERMINISTIC IN ITS SPAWNED EC TASKS (required for TestContext equality)
 */
trait GenIOInteropCats {

  /**
   * Given a generator for `A`, produces a generator for `IO[E, A]` using the `IO.point` constructor.
   */
  def genSyncSuccess[E, A: Arbitrary]: Gen[IO[E, A]] = Arbitrary.arbitrary[A].map(ZIO.succeed[A](_))

  /**
   * Given a generator for `A`, produces a generator for `IO[E, A]` using the `IO.async` constructor.
   */
  def genAsyncSuccess[E, A: Arbitrary]: Gen[IO[E, A]] =
    Arbitrary.arbitrary[A].map(a => ZIO.async[Any, E, A](k => k(ZIO.succeed(a))))

  /**
   * Randomly uses either `genSyncSuccess` or `genAsyncSuccess` with equal probability.
   */
  def genSuccess[E, A: Arbitrary]: Gen[IO[E, A]] = Gen.oneOf(genSyncSuccess[E, A], genAsyncSuccess[E, A])

  def genIO[E, A: Arbitrary]: Gen[IO[E, A]] = genSuccess[E, A]

  def genUIO[A: Arbitrary]: Gen[UIO[A]] =
    Gen.oneOf(genSuccess[Nothing, A], genIdentityTrans(genSuccess[Nothing, A]))

  /**
   * Given a generator for `IO[E, A]`, produces a sized generator for `IO[E, A]` which represents a transformation,
   * by using some random combination of the methods `map`, `flatMap`, `mapError`, and any other method that does not change
   * the success/failure of the value, but may change the value itself.
   */
  def genLikeTrans[E: Arbitrary: Cogen, A: Arbitrary: Cogen](gen: Gen[IO[E, A]]): Gen[IO[E, A]] = {
    val functions: IO[E, A] => Gen[IO[E, A]] = io =>
      Gen.oneOf(
        genOfFlatMaps[E, A](io)(genSuccess[E, A]),
        genOfMaps[E, A](io),
        genOfRace[E, A](io),
        genOfParallel[E, A](io)(genSuccess[E, A]),
        genOfMapErrors[E, A](io)
      )
    gen.flatMap(io => genTransformations(functions)(io))
  }

  /**
   * Given a generator for `IO[E, A]`, produces a sized generator for `IO[E, A]` which represents a transformation,
   * by using methods that can have no effect on the resulting value (e.g. `map(identity)`, `io.race(never)`, `io.par(io2).map(_._1)`).
   */
  def genIdentityTrans[E, A: Arbitrary](gen: Gen[IO[E, A]]): Gen[IO[E, A]] = {
    val functions: IO[E, A] => Gen[IO[E, A]] = io =>
      Gen.oneOf(
        genOfIdentityFlatMaps[E, A](io),
        genOfIdentityMaps[E, A](io),
        genOfIdentityMapErrors[E, A](io),
        genOfRace[E, A](io),
        genOfParallel[E, A](io)(genAsyncSuccess[E, A])
      )
    gen.flatMap(io => genTransformations(functions)(io))
  }

  private def genTransformations[E, A](
    functionGen: IO[E, A] => Gen[IO[E, A]]
  )(io: IO[E, A]): Gen[IO[E, A]] =
    Gen.sized { size =>
      def append1(n: Int, io: IO[E, A]): Gen[IO[E, A]] =
        if (n <= 0) io
        else
          (for {
            updatedIO <- functionGen(io)
          } yield updatedIO).flatMap(append1(n - 1, _))
      append1(size, io)
    }

  private def genOfMaps[E, A: Arbitrary: Cogen](io: IO[E, A]): Gen[IO[E, A]] =
    Arbitrary.arbitrary[A => A].map(f => io.map(f))

  private def genOfIdentityMaps[E, A](io: IO[E, A]): Gen[IO[E, A]] = Gen.const(io.map(identity))

  private def genOfMapErrors[E: Arbitrary: Cogen, A](io: IO[E, A]): Gen[IO[E, A]] =
    Arbitrary.arbitrary[E => E].map(f => io.mapError(f))

  private def genOfIdentityMapErrors[E, A](io: IO[E, A]): Gen[IO[E, A]] =
    Gen.const(io.mapError(identity))

  private def genOfFlatMaps[E, A](io: IO[E, A])(
    gen: Gen[IO[E, A]]
  ): Gen[IO[E, A]] =
    gen.map(nextIO => io.flatMap(_ => nextIO))

  private def genOfIdentityFlatMaps[E, A](io: IO[E, A]): Gen[IO[E, A]] =
    Gen.const(io.flatMap(a => ZIO.succeed(a)))

  private def genOfRace[E, A](io: IO[E, A]): Gen[IO[E, A]] =
    Gen.const(io.raceFirst(ZIO.never.interruptible))

  private def genOfParallel[E, A](io: IO[E, A])(gen: Gen[IO[E, A]]): Gen[IO[E, A]] =
    gen.map { parIo =>
      // this should work, but generates more random failures on CI
//      io.interruptible.zipPar(parIo.interruptible).map(_._1)
      Promise.make[Nothing, Unit].flatMap { p =>
        ZManaged
          .fromZIO(parIo *> p.succeed(()))
          .fork
          .useDiscard(p.await *> io)
      }
    }
}
