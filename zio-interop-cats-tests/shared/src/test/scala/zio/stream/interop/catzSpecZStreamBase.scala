package zio.stream.interop

import cats.Eq
import cats.effect.laws.util.TestContext
import cats.implicits._
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.interop.catz.taskEffectInstance
import zio.interop.catzSpecBase
import zio.stream._
import zio.{ Chunk, ZIO }

private[interop] trait catzSpecZStreamBase
    extends catzSpecBase
    with catzSpecZStreamBaseLowPriority
    with GenStreamInteropCats {

  implicit def chunkEq[A](implicit ev: Eq[A]): Eq[Chunk[A]] = (x: Chunk[A], y: Chunk[A]) => x.corresponds(y)(ev.eqv)

  implicit def zstreamEqStream[E: Eq, A: Eq](implicit tc: TestContext): Eq[Stream[E, A]] = Eq.by(_.either)

  implicit def zstreamEqUStream[A: Eq](implicit tc: TestContext): Eq[Stream[Nothing, A]] =
    Eq.by(ustream => taskEffectInstance.toIO(ustream.runCollect.sandbox.either))

  implicit def zstreamEqParIO[E: Eq, A: Eq](implicit tc: TestContext): Eq[ParStream[Any, E, A]] =
    Eq.by(Par.unwrap(_))

  implicit def zstreamArbitrary[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZStream[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[R => Stream[E, A]].map(ZStream.fromZIO(ZIO.environment[R]).flatMap(_)))

  implicit def streamArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[Stream[E, A]] =
    Arbitrary(Gen.oneOf(genStream[E, A], genLikeTrans(genStream[E, A]), genIdentityTrans(genStream[E, A])))

  implicit def zstreamParArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ParStream[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[Stream[E, A]].map(Par.apply))

}

private[interop] trait catzSpecZStreamBaseLowPriority { self: catzSpecZStreamBase =>

  implicit def zstreamEq[R: Arbitrary, E: Eq, A: Eq](implicit tc: TestContext): Eq[ZStream[R, E, A]] = {
    def run(r: R, zstream: ZStream[R, E, A]) = taskEffectInstance.toIO(zstream.runCollect.provide(r).either)
    Eq.instance(
      (stream1, stream2) =>
        Arbitrary.arbitrary[R].sample.fold(false)(r => catsSyntaxEq(run(r, stream1)) eqv run(r, stream2))
    )
  }

}
