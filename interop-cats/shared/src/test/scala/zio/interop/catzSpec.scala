package zio.interop

import cats.data.EitherT
import cats.effect.concurrent.Deferred
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.discipline._
import cats.effect.laws.{ AsyncLaws, ConcurrentEffectLaws, ConcurrentLaws, EffectLaws }
import cats.effect.{ Async, Concurrent, ConcurrentEffect, ContextShift, Effect }
import cats.implicits._
import cats.laws._
import cats.laws.discipline._
import cats.{ Eq, Monad }
import org.scalacheck.{ Arbitrary, Cogen, Prop }
import org.typelevel.discipline.Laws
import zio.interop.catz._
import zio.{ IO, _ }

import scala.concurrent.Promise

class catzSpec extends catzSpecBase {

  checkAllAsync(
    "ConcurrentEffect[Task]",
    implicit tc => ConcurrentEffectTestsOverrides[Task].concurrentEffect[Int, Int, Int]
  )
  (1 to 10).foreach { i =>
    checkAllAsync(
      s"duplicate-N:$i",
      implicit tc => ConcurrentEffectTestsOverrides[Task].concurrentEffect[Int, Int, Int]
    )
  }
  checkAllAsync("Effect[Task]", implicit tc => EffectTestsOverrides[Task].effect[Int, Int, Int])
  checkAllAsync("Concurrent[Task]", implicit tc => ConcurrentTestsOverrides[Task].concurrent[Int, Int, Int])
  checkAllAsync("MonadError[IO[Int, ?]]", implicit tc => MonadErrorTests[IO[Int, ?], Int].monadError[Int, Int, Int])
  checkAllAsync("MonoidK[IO[Int, ?]]", implicit tc => MonoidKTests[IO[Int, ?]].monoidK[Int])
  checkAllAsync("SemigroupK[IO[Option[Unit], ?]]", implicit tc => SemigroupKTests[IO[Option[Unit], ?]].semigroupK[Int])
  checkAllAsync("SemigroupK[Task]", implicit tc => SemigroupKTests[Task].semigroupK[Int])
  checkAllAsync("Bifunctor[IO]", implicit tc => BifunctorTests[IO].bifunctor[Int, Int, Int, Int, Int, Int])
  checkAllAsync("Parallel[Task]", implicit tc => ParallelTests[Task, Util.Par].parallel[Int, Int])
  checkAllAsync("Monad[UIO]", { implicit tc =>
    implicit def ioArbitrary[A: Arbitrary: Cogen]: Arbitrary[UIO[A]] = Arbitrary(genUIO[A])
    MonadTests[UIO].apply[Int, Int, Int]
  })

  // ZManaged Tests
  checkAllAsync("Monad[ZManaged]", implicit tc => MonadTests[ZManaged[Any, Throwable, ?]].apply[Int, Int, Int])
  checkAllAsync("Monad[ZManaged]", implicit tc => ExtraMonadTests[ZManaged[Any, Throwable, ?]].monadExtras[Int])
  checkAllAsync("SemigroupK[ZManaged]", implicit tc => SemigroupKTests[ZManaged[Any, Throwable, ?]].semigroupK[Int])
  checkAllAsync(
    "MonadError[ZManaged]",
    implicit tc => MonadErrorTests[ZManaged[Any, Int, ?], Int].monadError[Int, Int, Int]
  )

  object summoningInstancesTest {
    import cats._
    import cats.effect._

    Concurrent[RIO[String, ?]]
    Async[RIO[String, ?]]
    LiftIO[RIO[String, ?]]
    Sync[RIO[String, ?]]
    MonadError[RIO[String, ?], Throwable]
    Monad[RIO[String, ?]]
    Applicative[RIO[String, ?]]
    Functor[RIO[String, ?]]
    Parallel[RIO[String, ?], ParIO[String, Throwable, ?]]
    SemigroupK[RIO[String, ?]]
    implicitly[Parallel[RIO[String, ?]]]
    Apply[UIO]
    LiftIO[ZManaged[String, Throwable, ?]]
    MonadError[ZManaged[String, Throwable, ?], Throwable]
    Monad[ZManaged[String, Throwable, ?]]
    Applicative[ZManaged[String, Throwable, ?]]
    Functor[ZManaged[String, Throwable, ?]]
    SemigroupK[ZManaged[String, Throwable, ?]]

    def concurrentEffect[R: Runtime] = ConcurrentEffect[RIO[R, ?]]
    def effect[R: Runtime]           = Effect[RIO[R, ?]]
  }

  object summoningRuntimeInstancesTest {
    import cats.effect._
    import zio.interop.catz.implicits._

    ContextShift[Task]
    ContextShift[RIO[String, ?]]
    cats.effect.Clock[Task]
    Timer[Task]

    ContextShift[UIO]
    cats.effect.Clock[UIO]
    Timer[UIO]
    Monad[UIO]
  }
}

trait AsyncLawsOverrides[F[_]] extends AsyncLaws[F] {
  import cats.effect.ExitCase.{ Completed, Error }

  override def bracketReleaseIsCalledOnCompletedOrError[A, B](fa: F[A], b: B) = {
    val lh = F.asyncF[B] { cb =>
      F.bracketCase(F.pure(cb)) { _ =>
        fa.as(())
      } {
        case (cb, Completed | Error(_)) => F.delay(cb(Right(b)))
        case _                          => F.unit
      }
    }
//    lh <-> F.pure(b)
    F.pure(b) <-> lh
//    lh <-> fa.attempt.as(b)
  }

  def asyncFRaiseErrorIsNever[A](e: Throwable) =
    F.never[A] <-> F.asyncF[A](_ => F.raiseError(e))

  def asyncFIgnoredCallbackIsNever[A](fa: F[Unit]) =
    F.never[A] <-> F.asyncF[A](_ => fa)

  def asyncThrowIsRaiseError[A](e: Throwable) =
    F.async[A](_ => throw e) <-> F.raiseError(e)

  def asyncFThrowIsRaiseError[A](e: Throwable) =
    F.asyncF[A](_ => throw e) <-> F.raiseError(e)

}

trait AsyncTestsOverrides[F[_]] extends AsyncTests[F] {

  override def laws: AsyncLawsOverrides[F]

  override def async[A: Arbitrary: Eq, B: Arbitrary: Eq, C: Arbitrary: Eq](
    implicit
    ArbFA: Arbitrary[F[A]],
    ArbFB: Arbitrary[F[B]],
    ArbFC: Arbitrary[F[C]],
    ArbFU: Arbitrary[F[Unit]],
    ArbFAtoB: Arbitrary[F[A => B]],
    ArbFBtoC: Arbitrary[F[B => C]],
    ArbT: Arbitrary[Throwable],
    CogenA: Cogen[A],
    CogenB: Cogen[B],
    CogenC: Cogen[C],
    CogenT: Cogen[Throwable],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqFC: Eq[F[C]],
    EqFU: Eq[F[Unit]],
    EqT: Eq[Throwable],
    EqFEitherTU: Eq[F[Either[Throwable, Unit]]],
    EqFEitherTA: Eq[F[Either[Throwable, A]]],
    EqEitherTFTA: Eq[EitherT[F, Throwable, A]],
    EqFABC: Eq[F[(A, B, C)]],
    EqFInt: Eq[F[Int]],
    iso: SemigroupalTests.Isomorphisms[F],
    params: Parameters
  ): RuleSet = {
    val parent  = super.async[A, B, C]
    val default = parent.props
    new RuleSet {
      override def name: String                       = parent.name
      override def bases: Seq[(String, Laws#RuleSet)] = parent.bases
      override def parents: Seq[RuleSet]              = parent.parents
      override def props: Seq[(String, Prop)]         =
        // Activating the tests that detect non-termination only if allowed by Params,
        // because such tests might not be reasonable depending on evaluation model
        {
          import org.scalacheck.Prop.forAll
          (if (params.allowNonTerminationLaws)
             default ++ Seq(
               "asyncF raiseError is never"       -> forAll(laws.asyncFRaiseErrorIsNever[A] _),
               "asyncF ignored callback is never" -> forAll(laws.asyncFIgnoredCallbackIsNever[A] _)
             )
           else
             default) ++ Seq(
            "async throw is raiseError"  -> forAll(laws.asyncThrowIsRaiseError[A] _),
            "asyncF throw is raiseError" -> forAll(laws.asyncFThrowIsRaiseError[A] _)
          )
        }
//        default
    }
  }
}

trait ConcurrentEffectLawsOverrides[F[_]] extends ConcurrentEffectLaws[F] {
  import cats.effect.IO

  override def runCancelableIsSynchronous[A]: IsEq[F[Unit]] = {
    val lh = Deferred.uncancelable[F, Unit].flatMap { latch =>
      val spawned = Promise[Unit]()
      // Never ending task
      val ff = F.cancelable[A](_ => { spawned.success(()); latch.complete(()) })
      // Execute, then cancel
      val token = F.delay(F.runCancelable(ff)(_ => IO.unit).unsafeRunSync()).flatMap { canceler =>
        Async.fromFuture(F.pure(spawned.future)) >> canceler
      }
      F.liftIO(F.runAsync(token)(_ => IO.unit).toIO) *> latch.get
    }
    lh <-> F.unit
  }

}

object EffectTestsOverrides {

  def apply[F[_]](implicit ev: Effect[F]): EffectTests[F] =
    new EffectTests[F] with AsyncTestsOverrides[F] {
      final val laws = new EffectLaws[F] with AsyncLawsOverrides[F] {
        override val F: Effect[F] = ev
      }
    }
}

object ConcurrentTestsOverrides {

  def apply[F[_]](implicit ev: Concurrent[F], cs: ContextShift[F]): ConcurrentTests[F] =
    new ConcurrentTests[F] with AsyncTestsOverrides[F] {
      final val laws = new ConcurrentLaws[F] with AsyncLawsOverrides[F] {
        override val F: Concurrent[F]              = ev
        override val contextShift: ContextShift[F] = cs
      }
    }
}

object ConcurrentEffectTestsOverrides {

  def apply[F[_]](implicit ev: ConcurrentEffect[F], cs: ContextShift[F]): ConcurrentEffectTests[F] =
    new ConcurrentEffectTests[F] with AsyncTestsOverrides[F] {
      final val laws = new ConcurrentEffectLawsOverrides[F] with AsyncLawsOverrides[F] {
        override val F: ConcurrentEffect[F]        = ev
        override val contextShift: ContextShift[F] = cs
      }
    }
}
