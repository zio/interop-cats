package zio.interop

import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.*
import zio.managed.*

private[interop] trait ZioSpecBase extends CatsSpecBase with ZioSpecBaseLowPriority with GenIOInteropCats {

  implicit def arbitraryUIO[A: Arbitrary]: Arbitrary[UIO[A]] =
    Arbitrary(genUIO[A])

  implicit def arbitraryURIO[R: Cogen: Tag, A: Arbitrary]: Arbitrary[URIO[R, A]] =
    Arbitrary(Arbitrary.arbitrary[ZEnvironment[R] => UIO[A]].map(ZIO.environment[R].flatMap))

  implicit def arbitraryUManaged[A: Arbitrary]: Arbitrary[UManaged[A]] =
    zManagedArbitrary[Any, Nothing, A](arbitraryUIO[A])

  implicit def arbitraryURManaged[R: Cogen: Tag, A: Arbitrary]: Arbitrary[URManaged[R, A]] =
    zManagedArbitrary[R, Nothing, A]

  implicit def arbitraryCause[E](implicit e: Arbitrary[E]): Arbitrary[Cause[E]] = {
    lazy val self: Gen[Cause[E]] =
      Gen.oneOf(
        e.arbitrary.map(Cause.Fail(_, StackTrace.none)),
        Arbitrary.arbitrary[Throwable].map(Cause.Die(_, StackTrace.none)),
        // Generating interrupt failures causes law failures (`canceled`/`Outcome.Canceled` are ill-defined as of now https://github.com/zio/interop-cats/issues/503#issuecomment-1157101175=)
//        Gen.long.flatMap(l1 => Gen.long.map(l2 => Cause.Interrupt(Fiber.Id(l1, l2)))),
        Gen.delay(self.map(Cause.stack)),
        Gen.delay(self.map(Cause.stackless)),
        Gen.delay(self.flatMap(e1 => self.map(e2 => Cause.Both(e1, e2)))),
        Gen.delay(self.flatMap(e1 => self.map(e2 => Cause.Then(e1, e2)))),
        Gen.const(Cause.empty)
      )
    Arbitrary(self)
  }

  implicit def cogenCause[E]: Cogen[Cause[E]] =
    Cogen(_.hashCode.toLong)
}

private[interop] trait ZioSpecBaseLowPriority { self: ZioSpecBase =>

  implicit def arbitraryClock(implicit ticker: Ticker): Arbitrary[Clock] =
    Arbitrary(Gen.const(testClock))

  implicit val cogenForClock: Cogen[Clock] =
    Cogen(_.hashCode.toLong)

  implicit def arbitraryIO[E: CanFail: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] = {
    implicitly[CanFail[E]]
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))
  }

  implicit def arbitraryZIO[R: Cogen: Tag, E: CanFail: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Gen.function1[ZEnvironment[R], IO[E, A]](arbitraryIO[E, A].arbitrary).map(ZIO.environment[R].flatMap))

  implicit def arbitraryRIO[R: Cogen: Tag, A: Arbitrary: Cogen]: Arbitrary[RIO[R, A]] =
    arbitraryZIO[R, Throwable, A]

  implicit def arbitraryTask[A: Arbitrary: Cogen]: Arbitrary[Task[A]] =
    arbitraryIO[Throwable, A]

  def zManagedArbitrary[R, E, A](implicit zio: Arbitrary[ZIO[R, E, A]]): Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(zio.arbitrary.map(ZManaged.fromZIO(_)))

  implicit def arbitraryRManaged[R: Cogen: Tag, A: Arbitrary: Cogen]: Arbitrary[RManaged[R, A]] =
    zManagedArbitrary[R, Throwable, A]

  implicit def arbitraryManaged[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[Managed[E, A]] =
    zManagedArbitrary[Any, E, A]

  implicit def arbitraryZManaged[R: Cogen: Tag, E: Arbitrary: Cogen, A: Arbitrary: Cogen]
    : Arbitrary[ZManaged[R, E, A]] =
    zManagedArbitrary[R, E, A]

  implicit def arbitraryTaskManaged[A: Arbitrary: Cogen]: Arbitrary[TaskManaged[A]] =
    zManagedArbitrary[Any, Throwable, A]

  implicit def cogenZEnvironment[R: Cogen: Tag]: Cogen[ZEnvironment[R]] =
    Cogen[R].contramap(_.get)
}
