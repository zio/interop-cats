package zio.interop

import cats.Eq
import cats.effect.laws.util.TestContext
import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.{ IO, IsNotIntersection, Runtime, Tag, ZEnvironment, ZIO, ZManaged }

private[interop] trait catzSpecZIOBase extends catzSpecBase with GenIOInteropCats {

  implicit def zioEqParIO[E: Eq, A: Eq](implicit rts: Runtime[Any], tc: TestContext): Eq[ParIO[Any, E, A]] =
    Eq.by(Par.unwrap(_))

  implicit def zioArbitrary[R: Cogen: Tag: IsNotIntersection, E: Arbitrary: Cogen, A: Arbitrary: Cogen]
    : Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[ZEnvironment[R] => IO[E, A]].map(ZIO.environment[R].flatMap(_)))

  implicit def ioArbitrary[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))

  implicit def ioParArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ParIO[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(Par.apply))

  implicit def zManagedArbitrary[R, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(Arbitrary.arbitrary[IO[E, A]].map(ZManaged.fromZIO(_)))
}
