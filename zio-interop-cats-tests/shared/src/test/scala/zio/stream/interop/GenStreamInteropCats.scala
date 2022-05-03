package zio.stream.interop

import org.scalacheck.*
import zio.stream.*

trait GenStreamInteropCats {

  /**
   * Given a generator for `List[A]`, produces a generator for `Stream[E, A]` using the `Stream.fromIterable` constructor.
   */
  def genSuccess[E, A: Arbitrary]: Gen[Stream[E, A]] = Arbitrary.arbitrary[A].map(ZStream.succeed(_))

  /**
   * Given a generator for `E`, produces a generator for `Stream[E, A]` using the `Stream.fail` constructor.
   */
  def genFailure[E: Arbitrary, A]: Gen[Stream[E, A]] = Arbitrary.arbitrary[E].map(ZStream.fail[E](_))

  /**
   * Randomly uses either `genSuccess` or `genFailure` with equal probability.
   */
  def genStream[E: Arbitrary, A: Arbitrary]: Gen[Stream[E, A]] =
    Gen.oneOf(genSuccess[E, A], genFailure[E, A])

  /**
   * Given a generator for `Stream[E, A]`, produces a sized generator for `Stream[E, A]` which represents a transformatstreamn,
   * by using some random combinatstreamn of the methods `map`, `flatMap`, `mapError`, and any other method that does not change
   * the success/failure of the value, but may change the value itself.
   */
  def genLikeTrans[E: Arbitrary: Cogen, A: Arbitrary: Cogen](gen: Gen[Stream[E, A]]): Gen[Stream[E, A]] = {
    val functstreamns: Stream[E, A] => Gen[Stream[E, A]] = stream =>
      Gen.oneOf(
        genOfFlatMaps[E, A](stream)(genSuccess[E, A]),
        genOfMaps[E, A](stream),
        genOfMapErrors[E, A](stream)
      )
    gen.flatMap(stream => genTransformations(functstreamns)(stream))
  }

  /**
   * Given a generator for `Stream[E, A]`, produces a sized generator for `Stream[E, A]` which represents a transformatstreamn,
   * by using methods that can have no effect on the resulting value (e.g. `map(identity)`).
   */
  def genIdentityTrans[E, A](gen: Gen[Stream[E, A]]): Gen[Stream[E, A]] = {
    val functstreamns: Stream[E, A] => Gen[Stream[E, A]] = stream =>
      Gen.oneOf(
        genOfIdentityFlatMaps[E, A](stream),
        genOfIdentityMaps[E, A](stream),
        genOfIdentityMapErrors[E, A](stream)
      )
    gen.flatMap(stream => genTransformations(functstreamns)(stream))
  }

  private def genTransformations[E, A](
    functstreamnGen: Stream[E, A] => Gen[Stream[E, A]]
  )(stream: Stream[E, A]): Gen[Stream[E, A]] =
    Gen.sized { size =>
      def append1(n: Int, stream: Stream[E, A]): Gen[Stream[E, A]] =
        if (n <= 0) stream
        else
          (for {
            updatedIO <- functstreamnGen(stream)
          } yield updatedIO).flatMap(append1(n - 1, _))
      append1(size, stream)
    }

  private def genOfMaps[E, A: Arbitrary: Cogen](stream: Stream[E, A]): Gen[Stream[E, A]] =
    Arbitrary.arbitrary[A => A].map(f => stream.map(f))

  private def genOfIdentityMaps[E, A](stream: Stream[E, A]): Gen[Stream[E, A]] = Gen.const(stream.map(identity))

  private def genOfMapErrors[E: Arbitrary: Cogen, A](stream: Stream[E, A]): Gen[Stream[E, A]] =
    Arbitrary.arbitrary[E => E].map(f => stream.mapError(f))

  private def genOfIdentityMapErrors[E, A](stream: Stream[E, A]): Gen[Stream[E, A]] =
    Gen.const(stream.mapError(identity))

  private def genOfFlatMaps[E, A](stream: Stream[E, A])(
    gen: Gen[Stream[E, A]]
  ): Gen[Stream[E, A]] =
    gen.map(nextIO => stream.flatMap(_ => nextIO))

  private def genOfIdentityFlatMaps[E, A](stream: Stream[E, A]): Gen[Stream[E, A]] =
    Gen.const(stream.flatMap(a => ZStream.succeed(a)))

}
