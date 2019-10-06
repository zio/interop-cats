package zio.interop

import cats.Eq
import cats.effect.laws.util.TestContext
import cats.implicits._
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.interop.catz.taskEffectInstance
import zio.{ Cause, IO, UIO, ZIO, ZManaged }

private[interop] trait catzSpecZIOBase extends catzSpecBase with GenIOInteropCats {

  implicit def zioEqParIO[E: Eq, A: Eq](implicit tc: TestContext): Eq[ParIO[Any, E, A]] =
    Eq.by(Par.unwrap(_))

  implicit def zioEqZManaged[E: Eq, A: Eq](implicit tc: TestContext): Eq[ZManaged[Any, E, A]] =
    Eq.by(_.reserve.flatMap(_.acquire).either)

  implicit def zioArbitrary[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[R => IO[E, A]].map(ZIO.environment[R].flatMap(_)))

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))

  implicit def ioParArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ParIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(Par.apply))

  implicit def zManagedArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(ZManaged.fromEffect(_)))

}
