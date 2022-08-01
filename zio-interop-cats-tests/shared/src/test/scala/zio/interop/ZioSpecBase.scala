package zio.interop

import org.scalacheck.{ Arbitrary, Cogen, Gen }
import zio.*
import zio.internal.stacktracer.Tracer
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
        Arbitrary
          .arbitrary[Int]
          .flatMap(l1 =>
            Arbitrary.arbitrary[Int].map(l2 => Cause.Interrupt(FiberId(l1, l2, Tracer.instance.empty), StackTrace.none))
          ),
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
    import zio.interop.catz.generic.concurrentInstanceCause
    Arbitrary(
      Gen.oneOf(
        genIO[E, A],
        genLikeTrans(genIO[E, A]),
        genIdentityTrans(genIO[E, A])
      )
    )
  }

  implicit def arbitraryZIO[R: Cogen: Tag, E: CanFail: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Gen.function1[ZEnvironment[R], IO[E, A]](arbitraryIO[E, A].arbitrary).map(ZIO.environment[R].flatMap))

  implicit def arbitraryTask[A: Arbitrary: Cogen](implicit ticker: Ticker): Arbitrary[Task[A]] = {
    val arbIO = arbitraryIO[Throwable, A]
    if (catsConversionGenerator)
      Arbitrary(Gen.oneOf(arbIO.arbitrary, genCatsConversionTask[A]))
    else
      arbIO
  }

  def genCatsConversionTask[A: Arbitrary: Cogen](implicit ticker: Ticker): Gen[Task[A]] =
    arbitraryIO[A].arbitrary.map(liftIO(_))

  def liftIO[A](io: cats.effect.IO[A])(implicit ticker: Ticker): zio.Task[A] =
    ZIO.asyncInterrupt { k =>
      val (result, cancel) = io.unsafeToFutureCancelable()
      k(ZIO.fromFuture(_ => result).tapError {
        case c: scala.concurrent.CancellationException if c.getMessage == "The fiber was canceled" =>
          zio.interop.catz.concurrentInstance.canceled *> ZIO.interrupt
        case _                                                                                     =>
          ZIO.unit
      })
      Left(ZIO.fromFuture(_ => cancel()).orDie)
    }

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
