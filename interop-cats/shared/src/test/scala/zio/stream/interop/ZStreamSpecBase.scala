package zio.stream.interop

import cats.Eq
import cats.syntax.all._
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.interop.CatsSpecBase
import zio.stream._
import zio.{ CanFail, Chunk, ZIO }

private[interop] trait ZStreamSpecBase extends CatsSpecBase with ZStreamSpecBaseLowPriority with GenStreamInteropCats {

  implicit def eqForChunk[A: Eq]: Eq[Chunk[A]] =
    (x, y) => x.corresponds(y)(_ eqv _)

  implicit def eqForUStream[A: Eq](implicit ticker: Ticker): Eq[UStream[A]] =
    zStreamEq[Any, Nothing, A]

  implicit def arbitraryUStream[A: Arbitrary]: Arbitrary[UStream[A]] =
    Arbitrary(Gen.oneOf(genSuccess[Nothing, A], genIdentityTrans(genSuccess[Nothing, A])))
}

private[interop] trait ZStreamSpecBaseLowPriority { self: ZStreamSpecBase =>

  def zStreamEq[R, E, A](implicit zio: Eq[ZIO[R, E, Chunk[A]]]): Eq[ZStream[R, E, A]] =
    Eq.by(_.runCollect)

  implicit def eqForStream[E: Eq, A: Eq](implicit ticker: Ticker): Eq[Stream[E, A]] =
    zStreamEq[Any, E, A]

  implicit def eqForZStream[R: Arbitrary, E: Eq, A: Eq](implicit ticker: Ticker): Eq[ZStream[R, E, A]] =
    zStreamEq[R, E, A]

  implicit def arbitraryStream[E: CanFail: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[Stream[E, A]] = {
    implicitly[CanFail[E]]
    Arbitrary(Gen.oneOf(genStream[E, A], genLikeTrans(genStream[E, A]), genIdentityTrans(genStream[E, A])))
  }

  implicit def arbitraryZStream[
    R: Cogen,
    E: CanFail: Arbitrary: Cogen,
    A: Arbitrary: Cogen
  ]: Arbitrary[ZStream[R, E, A]] = Arbitrary(
    Gen
      .function1[R, Stream[E, A]](arbitraryStream[E, A].arbitrary)
      .map(ZStream.fromEffect(ZIO.environment[R]).flatMap)
  )
}
