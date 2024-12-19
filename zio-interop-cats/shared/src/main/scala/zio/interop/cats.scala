/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.interop

import cats.data.State
import cats.effect.kernel.*
import cats.effect.unsafe.IORuntime
import cats.effect.{ IO as CIO, LiftIO }
import cats.kernel.{ CommutativeMonoid, CommutativeSemigroup }
import cats.effect
import cats.*
import zio.{ Fiber, Ref as ZRef }
import zio.*
import zio.Clock.{ currentTime, nanoTime }
import zio.Duration
import zio.internal.FiberScope
import zio.internal.stacktracer.{ InteropTracer, Tracer as CoreTracer }

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.*

object catz extends CatsEffectPlatform {
  object core extends CatsPlatform
  object mtl  extends CatsMtlPlatform

  /**
   * `import zio.interop.catz.implicits._` brings in the default Runtime in order to
   * convert ZIO to Cats Effect without the ceremony of
   *
   * {{{
   * ZIO.runtime[Any].flatMap { implicit runtime =>
   *  val cio: cats.effect.IO[Unit] = ZIO.debug("Hello world").toEffect[cats.effect.IO]
   *  ...
   * }
   * }}}
   */
  object implicits {
    implicit val rts: Runtime[Any] = Runtime.default
  }

  /**
   * `import zio.interop.catz.generic._` brings in instances of
   * `GenConcurrent` and `GenTemporal`,`MonadCancel` and `MonadError`
   * for arbitrary non-Throwable `E` error type.
   *
   * These instances have somewhat different semantics than the instances
   * in `catz` however - they operate on `Cause[E]` errors. Meaning that
   * cats `ApplicativeError#handleErrorWith` operation can now recover from
   * `ZIO.die` and other non-standard ZIO errors not supported by cats IO.
   *
   * However, in cases where an instance such as `MonadCancel[F, _]` is
   * required by a function, these differences should not normally affect behavior -
   * by ignoring the error type, such a function signals that it does not
   * inspect the errors, but only uses `bracket` portion of `MonadCancel` for finalization.
   */
  object generic extends CatsEffectInstancesCause
}

abstract class CatsEffectPlatform
    extends CatsEffectInstances
    with CatsEffectZManagedInstances
    with CatsZManagedInstances
    with CatsChunkInstances
    with CatsNonEmptyChunkInstances
    with CatsZManagedSyntax {

  trait CatsApp extends ZIOAppDefault {
    implicit override val runtime: Runtime[Any] = super.runtime
  }

  val console: interop.console.cats.type =
    interop.console.cats
}

abstract class CatsPlatform
    extends CatsZioInstances
    with CatsZManagedInstances
    with CatsChunkInstances
    with CatsNonEmptyChunkInstances

abstract class CatsEffectInstances extends CatsZioInstances {

  implicit final def liftIOInstance[R](implicit runtime: IORuntime): LiftIO[RIO[R, _]] =
    new ZioLiftIO

  implicit final def asyncInstance[R]: Async[RIO[R, _]] =
    asyncInstance0.asInstanceOf[Async[RIO[R, _]]]

  implicit final def temporalInstance[R]: GenTemporal[ZIO[R, Throwable, _], Throwable] = asyncInstance[R]

  implicit final def concurrentInstance[R]: GenConcurrent[ZIO[R, Throwable, _], Throwable] = asyncInstance[R]

  private[this] val asyncInstance0: Async[Task] =
    new ZioAsync

  // bincompat only
  private[CatsEffectInstances] implicit final def asyncRuntimeInstance[E](implicit
    runtime: Runtime[Any]
  ): Async[Task] = {
    val _ = runtime
    asyncInstance[Any]
  }

  private[CatsEffectInstances] implicit final def temporalRuntimeInstance(implicit
    runtime: Runtime[Any]
  ): GenTemporal[IO[Throwable, _], Throwable] = {
    val _ = runtime
    temporalInstance[Any]
  }

}

sealed abstract class CatsEffectInstancesCause extends CatsZioInstances {

  implicit final def temporalInstanceCause[R, E]: GenTemporal[ZIO[R, E, _], Cause[E]] =
    temporalInstance1.asInstanceOf[GenTemporal[ZIO[R, E, _], Cause[E]]]

  implicit final def concurrentInstanceCause[R, E]: GenConcurrent[ZIO[R, E, _], Cause[E]] = temporalInstanceCause[R, E]

  private[this] val temporalInstance1: GenTemporal[ZIO[Any, Any, _], Cause[Any]] =
    new ZioTemporal[Any, Any, Cause[Any]] with ZioMonadErrorExitCause[Any, Any]

  // bincompat only
  private[CatsEffectInstancesCause] implicit final def temporalRuntimeInstanceCause[E](implicit
    runtime: Runtime[Any]
  ): GenTemporal[IO[E, _], Cause[E]] = {
    val _ = runtime
    temporalInstanceCause[Any, E]
  }
}

abstract class CatsZioInstances extends CatsZioInstances1 {
  type ParZIO[R, E, A] = ParallelF[ZIO[R, E, _], A]

  implicit final def monoidInstance[R, E, A: Monoid]: Monoid[ZIO[R, E, A]] =
    new ZioMonoid

  implicit final def parMonoidInstance[R, E, A: CommutativeMonoid]: CommutativeMonoid[ParZIO[R, E, A]] =
    new ZioParMonoid

  implicit final def monoidKInstance[R, E: Monoid]: MonoidK[ZIO[R, E, _]] =
    new ZioMonoidK

  implicit final def deferInstance[R, E]: Defer[ZIO[R, E, _]] =
    deferInstance0.asInstanceOf[Defer[ZIO[R, E, _]]]

  implicit final def bifunctorInstance[R]: Bifunctor[ZIO[R, _, _]] =
    bifunctorInstance0.asInstanceOf[Bifunctor[ZIO[R, _, _]]]

  private[this] val deferInstance0: Defer[UIO] =
    new ZioDefer[Any, Nothing]

  private[this] val bifunctorInstance0: Bifunctor[IO] =
    new ZioBifunctor[Any]
}

sealed abstract class CatsZioInstances1 extends CatsZioInstances2 {

  implicit final def parallelInstance[R, E]: Parallel.Aux[ZIO[R, E, _], ParallelF[ZIO[R, E, _], _]] =
    parallelInstance0.asInstanceOf[Parallel.Aux[ZIO[R, E, _], ParallelF[ZIO[R, E, _], _]]]

  implicit final def commutativeApplicativeInstance[R, E]: CommutativeApplicative[ParallelF[ZIO[R, E, _], _]] =
    commutativeApplicativeInstance0.asInstanceOf[CommutativeApplicative[ParallelF[ZIO[R, E, _], _]]]

  implicit final def semigroupInstance[R, E, A: Semigroup]: Semigroup[ZIO[R, E, A]] =
    new ZioSemigroup

  implicit final def parSemigroupInstance[R, E, A: CommutativeSemigroup]
    : CommutativeSemigroup[ParallelF[ZIO[R, E, _], A]] =
    new ZioParSemigroup

  implicit final def semigroupKInstance[R, E]: SemigroupK[ZIO[R, E, _]] =
    semigroupKInstance0.asInstanceOf[SemigroupK[ZIO[R, E, _]]]

  private[this] lazy val parallelInstance0: Parallel.Aux[Task, ParallelF[Task, _]] =
    new ZioParallel

  private[this] lazy val commutativeApplicativeInstance0: CommutativeApplicative[ParallelF[Task, _]] =
    new ZioParApplicative

  private[this] val semigroupKInstance0: SemigroupK[Task] =
    new ZioSemigroupK[Any, Throwable]
}

sealed abstract class CatsZioInstances2 {

  implicit final def monadErrorInstance[R, E]: MonadError[ZIO[R, E, _], E] =
    monadErrorInstance0.asInstanceOf[MonadError[ZIO[R, E, _], E]]

  private[this] val monadErrorInstance0: MonadError[Task, Throwable] =
    new ZioMonadError[Any, Throwable, Throwable] with ZioMonadErrorE[Any, Throwable]
}

private class ZioDefer[R, E] extends Defer[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def defer[A](fa: => F[A]): F[A] = {
    val byName: () => F[A] = () => fa
    ZIO.suspendSucceed(fa)(InteropTracer.newTrace(byName))
  }
}

private abstract class ZioConcurrent[R, E, E1]
    extends ZioMonadErrorExit[R, E, E1]
    with GenConcurrent[ZIO[R, E, _], E1] {

  private def toFiber[A](interrupted: zio.Ref[Boolean])(fiber: Fiber[E, A]): effect.Fiber[F, E1, A] =
    new effect.Fiber[F, E1, A] {
      override final val cancel: F[Unit]            = fiber.interrupt.unit
      override final val join: F[Outcome[F, E1, A]] =
        fiber.await.flatMap[R, E, Outcome[F, E1, A]]((exit: Exit[E, A]) => toOutcomeOtherFiber[A](interrupted)(exit))
    }

  private def toThrowableOrFiberFailure(error: E): Throwable =
    error match {
      case t: Throwable => t
      case _            => FiberFailure(Cause.fail(error))
    }

  override def ref[A](a: A): F[effect.Ref[F, A]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    ZRef.make(a).map(new ZioRef(_))
  }

  override def deferred[A]: F[Deferred[F, A]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    Promise.make[E, A].map(new ZioDeferred(_))
  }

  override final def start[A](fa: F[A]): F[effect.Fiber[F, E1, A]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    for {
      interrupted <- zio.Ref.make(true) // fiber could be interrupted before executing a single op
      fiber       <- signalOnNoExternalInterrupt(fa.interruptible)(interrupted.set(false)).forkDaemon
    } yield toFiber(interrupted)(fiber)
  }

  override def never[A]: F[A] =
    ZIO.never(CoreTracer.newTrace)

  override final def cede: F[Unit] =
    ZIO.yieldNow(CoreTracer.newTrace)

  override final def forceR[A, B](fa: F[A])(fb: F[B]): F[B] = {
    implicit def trace: Trace = CoreTracer.newTrace

    fa.foldCauseZIO(
      cause =>
        if (cause.isInterrupted)
          ZIO.descriptorWith(descriptor => if (descriptor.interrupters.nonEmpty) ZIO.failCause(cause) else fb)
        else fb,
      _ => fb
    )
  }

  override final def uncancelable[A](body: Poll[F] => F[A]): F[A] = {
    implicit def trace: Trace = InteropTracer.newTrace(body)

    ZIO.uninterruptibleMask(restore => body(toPoll(restore)))
  }

  override final def canceled: F[Unit] = {
    def loopUntilInterrupted: UIO[Unit] =
      ZIO.descriptorWith(d => if (d.interrupters.isEmpty) ZIO.yieldNow *> loopUntilInterrupted else ZIO.unit)

    for {
      _ <- ZIO.withFiberRuntime[Any, Nothing, Unit]((thisFiber, _) => thisFiber.interruptAsFork(thisFiber.id))
      _ <- loopUntilInterrupted
    } yield ()
  }

  override final def onCancel[A](fa: F[A], fin: F[Unit]): F[A] =
    guaranteeCase(fa) { case Outcome.Canceled() => fin.orDieWith(toThrowableOrFiberFailure); case _ => ZIO.unit }

  override final def memoize[A](fa: F[A]): F[F[A]] =
    fa.memoize(CoreTracer.newTrace)

  override final def racePair[A, B](
    fa: F[A],
    fb: F[B]
  ): ZIO[R, Nothing, Either[(Outcome[F, E1, A], effect.Fiber[F, E1, B]), (effect.Fiber[F, E1, A], Outcome[F, E1, B])]] =
    for {
      interruptedA <- zio.Ref.make(true)
      interruptedB <- zio.Ref.make(true)
      res          <- raceWith(
                        signalOnNoExternalInterrupt(fa.interruptible)(interruptedA.set(false)),
                        signalOnNoExternalInterrupt(fb.interruptible)(interruptedB.set(false))
                      )(
                        (exit, fiber) =>
                          toOutcomeOtherFiber(interruptedA)(exit).map(outcome => Left((outcome, toFiber(interruptedB)(fiber)))),
                        (exit, fiber) =>
                          toOutcomeOtherFiber(interruptedB)(exit).map(outcome => Right((toFiber(interruptedA)(fiber), outcome)))
                      )
    } yield res

  // delegate race & both to default implementations, because `raceFirst` & `zipPar` semantics do not match them
  override final def race[A, B](fa: F[A], fb: F[B]): F[Either[A, B]] = super.race(fa, fb)
  override final def both[A, B](fa: F[A], fb: F[B]): F[(A, B)]       = super.both(fa, fb)

  override final def guarantee[A](fa: F[A], fin: F[Unit]): F[A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    fa.ensuring(fin.orDieWith(toThrowableOrFiberFailure))
  }

  override final def guaranteeCase[A](fa: ZIO[R, E, A])(
    fin: Outcome[ZIO[R, E, _], E1, A] => ZIO[R, E, Unit]
  ): ZIO[R, E, A] =
    fa.onExit(exit => toOutcomeThisFiber(exit).flatMap(fin).orDieWith(toThrowableOrFiberFailure))

  override final def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] = {
    implicit def trace: Trace = InteropTracer.newTrace(use)

    ZIO.acquireReleaseWith(acquire)(release.andThen(_.orDieWith(toThrowableOrFiberFailure)))(use)
  }

  override final def bracketCase[A, B](acquire: ZIO[R, E, A])(use: A => ZIO[R, E, B])(
    release: (A, Outcome[ZIO[R, E, _], E1, B]) => ZIO[R, E, Unit]
  ): ZIO[R, E, B] = {
    implicit def trace: Trace = InteropTracer.newTrace(use)

    def handleRelease(a: A, exit: Exit[E, B]): URIO[R, Any] =
      toOutcomeThisFiber(exit).flatMap(release(a, _)).orDieWith(toThrowableOrFiberFailure)

    ZIO.acquireReleaseExitWith(acquire)(handleRelease)(use)
  }

  override final def bracketFull[A, B](acquire: Poll[ZIO[R, E, _]] => ZIO[R, E, A])(use: A => ZIO[R, E, B])(
    release: (A, Outcome[ZIO[R, E, _], E1, B]) => ZIO[R, E, Unit]
  ): ZIO[R, E, B] = {
    implicit def trace: Trace = InteropTracer.newTrace(use)

    ZIO.uninterruptibleMask[R, E, B] { restore =>
      acquire(toPoll(restore)).flatMap { a =>
        ZIO
          .suspendSucceed(restore(use(a)))
          .exit
          .flatMap { e =>
            ZIO
              .suspendSucceed(
                toOutcomeThisFiber(e).flatMap(release(a, _))
              )
              .foldCauseZIO(
                cause2 => ZIO.failCause(e.foldExit(_ ++ cause2, _ => cause2)),
                _ => e
              )
          }
      }
    }
  }

  override def unique: F[Unique.Token] =
    ZIO.succeed(new Unique.Token)(CoreTracer.newTrace)

  /**
   * An implementation of `raceWith` that forks the left and right fibers in
   * the global scope instead of the scope of the parent fiber.
   */
  private final def raceWith[R, E, E2, E3, A, B, C](left: ZIO[R, E, A], right: => ZIO[R, E2, B])(
    leftDone: (Exit[E, A], Fiber[E2, B]) => ZIO[R, E3, C],
    rightDone: (Exit[E2, B], Fiber[E, A]) => ZIO[R, E3, C]
  )(implicit trace: Trace): ZIO[R, E3, C] =
    raceFibersWith(left, right)(
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[A] =>
            winner.inheritAll.flatMap(_ => leftDone(exit, loser))
          case exit: Exit.Failure[E] =>
            leftDone(exit, loser)
        },
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[B]  =>
            winner.inheritAll.flatMap(_ => rightDone(exit, loser))
          case exit: Exit.Failure[E2] =>
            rightDone(exit, loser)
        }
    )

  /**
   * An implementation of `raceFibersWith` that forks the left and right fibers
   * in the global scope instead of the scope of the parent fiber.
   */
  private final def raceFibersWith[R, E, E2, E3, A, B, C](left: ZIO[R, E, A], right: ZIO[R, E2, B])(
    leftWins: (Fiber.Runtime[E, A], Fiber.Runtime[E2, B]) => ZIO[R, E3, C],
    rightWins: (Fiber.Runtime[E2, B], Fiber.Runtime[E, A]) => ZIO[R, E3, C]
  )(implicit trace: Trace): ZIO[R, E3, C] =
    ZIO.withFiberRuntime[R, E3, C] { (parentFiber, parentStatus) =>
      import java.util.concurrent.atomic.AtomicBoolean

      val parentRuntimeFlags = parentStatus.runtimeFlags

      @inline def complete[E, E2, A, B](
        winner: Fiber.Runtime[E, A],
        loser: Fiber.Runtime[E2, B],
        cont: (Fiber.Runtime[E, A], Fiber.Runtime[E2, B]) => ZIO[R, E3, C],
        ab: AtomicBoolean,
        cb: ZIO[R, E3, C] => Any
      ): Any =
        if (ab.compareAndSet(true, false)) {
          cb(cont(winner, loser))
        }

      val raceIndicator = new AtomicBoolean(true)

      val leftFiber  =
        ZIO.unsafe.makeChildFiber(trace, left, parentFiber, parentRuntimeFlags, FiberScope.global)(Unsafe.unsafe)
      val rightFiber =
        ZIO.unsafe.makeChildFiber(trace, right, parentFiber, parentRuntimeFlags, FiberScope.global)(Unsafe.unsafe)

      val startLeftFiber  = leftFiber.startSuspended()(Unsafe.unsafe)
      val startRightFiber = rightFiber.startSuspended()(Unsafe.unsafe)

      ZIO
        .async[R, E3, C](
          { cb =>
            leftFiber.addObserver { _ =>
              complete(leftFiber, rightFiber, leftWins, raceIndicator, cb)
              ()
            }(Unsafe.unsafe)

            rightFiber.addObserver { _ =>
              complete(rightFiber, leftFiber, rightWins, raceIndicator, cb)
              ()
            }(Unsafe.unsafe)

            startLeftFiber(left)
            startRightFiber(right)
            ()
          },
          leftFiber.id <> rightFiber.id
        )
        .onInterrupt(
          leftFiber.interruptFork *>
            rightFiber.interruptFork *>
            leftFiber.await *>
            rightFiber.await
        )
    }
}

private final class ZioDeferred[R, E, A](promise: Promise[E, A]) extends Deferred[ZIO[R, E, _], A] {
  type F[T] = ZIO[R, E, T]

  override val get: F[A] =
    promise.await(CoreTracer.newTrace)

  override def complete(a: A): F[Boolean] =
    promise.succeed(a)(CoreTracer.newTrace)

  override val tryGet: F[Option[A]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    promise.isDone.flatMap {
      case true  => get.asSome
      case false => ZIO.none
    }
  }
}

private final class ZioRef[R, E, A](ref: ZRef[A]) extends effect.Ref[ZIO[R, E, _], A] {
  type F[T] = ZIO[R, E, T]

  override def access: F[(A, A => F[Boolean])] = {
    implicit def trace: Trace = CoreTracer.newTrace

    get.map { current =>
      val called                   = new AtomicBoolean(false)
      def setter(a: A): F[Boolean] =
        ZIO.suspendSucceed {
          if (called.getAndSet(true)) {
            ZIO.succeed(false)
          } else {
            ref.modify { updated =>
              if (current == updated) (true, a)
              else (false, updated)
            }
          }
        }

      (current, setter)
    }
  }

  override def tryUpdate(f: A => A): F[Boolean] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    update(f).as(true)
  }

  override def tryModify[B](f: A => (A, B)): F[Option[B]] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    modify(f).asSome
  }

  override def update(f: A => A): F[Unit] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ref.update(f)
  }

  override def modify[B](f: A => (A, B)): F[B] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ref.modify(f(_).swap)
  }

  override def tryModifyState[B](state: State[A, B]): F[Option[B]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    modifyState(state).asSome
  }

  override def modifyState[B](state: State[A, B]): F[B] =
    modify(state.run(_).value)

  override def set(a: A): F[Unit] =
    ref.set(a)(CoreTracer.newTrace)

  override def get: F[A] =
    ref.get(CoreTracer.newTrace)
}

private abstract class ZioTemporal[R, E, E1] extends ZioConcurrent[R, E, E1] with GenTemporal[ZIO[R, E, _], E1] {

  override def sleep(time: FiniteDuration): F[Unit] = {
    implicit def trace: Trace = CoreTracer.newTrace

    ZIO.sleep(Duration.fromScala(time))
  }

  override def monotonic: F[FiniteDuration] = {
    implicit def trace: Trace = CoreTracer.newTrace

    nanoTime.map(FiniteDuration(_, NANOSECONDS))
  }

  override def realTime: F[FiniteDuration] = {
    implicit def trace: Trace = CoreTracer.newTrace

    currentTime(MILLISECONDS).map(FiniteDuration(_, MILLISECONDS))
  }
}

private abstract class ZioMonadError[R, E, E1] extends MonadError[ZIO[R, E, _], E1] with StackSafeMonad[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def pure[A](a: A): F[A] =
    ZIO.succeed(a)

  override final def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fa.map(f)
  }

  override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fa.flatMap(f)
  }

  override final def flatTap[A, B](fa: F[A])(f: A => F[B]): F[A] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fa.tap(f)
  }

  override final def widen[A, B >: A](fa: F[A]): F[B] =
    fa

  override final def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fa.zipWith(fb)(f)
  }

  override final def as[A, B](fa: F[A], b: B): F[B] =
    fa.as(b)(CoreTracer.newTrace)

  override final def whenA[A](cond: Boolean)(f: => F[A]): F[Unit] = {
    val byName: () => F[A]    = () => f
    implicit def trace: Trace = InteropTracer.newTrace(byName)

    ZIO.when(cond)(f).unit
  }

  override final def unit: F[Unit] =
    ZIO.unit

  override final def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = {
    def loop(a: A): F[B] = f(a).flatMap {
      case Left(a)  => loop(a)
      case Right(b) => ZIO.succeed(b)
    }

    ZIO.suspendSucceed(loop(a))
  }
}

private trait ZioMonadErrorE[R, E] extends ZioMonadError[R, E, E] {

  override final def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fa.catchAll(f)
  }

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    fa.catchSome(pf)
  }

  override final def raiseError[A](e: E): F[A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    ZIO.fail(e)
  }

  override final def attempt[A](fa: F[A]): F[Either[E, A]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    fa.either
  }

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[E, E]): F[A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    fa.mapError(pf.orElse { case error => error })
  }
}

private trait ZioMonadErrorCause[R, E] extends ZioMonadError[R, E, Cause[E]] {

  override final def handleErrorWith[A](fa: F[A])(f: Cause[E] => F[A]): F[A] =
    fa.catchAllCause(f)

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[Cause[E], F[A]]): F[A] =
    fa.catchSomeCause(pf)

  override final def raiseError[A](e: Cause[E]): F[A] =
    ZIO.failCause(e)

  override final def attempt[A](fa: F[A]): F[Either[Cause[E], A]] =
    fa.sandbox.either

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[Cause[E], Cause[E]]): F[A] =
    fa.mapErrorCause(pf.orElse { case error => error })
}

private abstract class ZioMonadErrorExit[R, E, E1] extends ZioMonadError[R, E, E1] {
  protected def toOutcomeThisFiber[A](exit: Exit[E, A]): UIO[Outcome[F, E1, A]]
  protected def toOutcomeOtherFiber[A](interruptedHandle: zio.Ref[Boolean])(exit: Exit[E, A]): UIO[Outcome[F, E1, A]]
}

private trait ZioMonadErrorExitThrowable[R]
    extends ZioMonadErrorExit[R, Throwable, Throwable]
    with ZioMonadErrorE[R, Throwable] {

  override final protected def toOutcomeThisFiber[A](exit: Exit[Throwable, A]): UIO[Outcome[F, Throwable, A]] =
    toOutcomeThrowableThisFiber(exit)

  protected final def toOutcomeOtherFiber[A](interruptedHandle: zio.Ref[Boolean])(
    exit: Exit[Throwable, A]
  ): UIO[Outcome[F, Throwable, A]] =
    interruptedHandle.get.map(toOutcomeThrowableOtherFiber(_)(ZIO.succeed(_), exit))
}

private trait ZioMonadErrorExitCause[R, E] extends ZioMonadErrorExit[R, E, Cause[E]] with ZioMonadErrorCause[R, E] {

  override protected def toOutcomeThisFiber[A](exit: Exit[E, A]): UIO[Outcome[F, Cause[E], A]] =
    toOutcomeCauseThisFiber(exit)

  protected final def toOutcomeOtherFiber[A](interruptedHandle: zio.Ref[Boolean])(
    exit: Exit[E, A]
  ): UIO[Outcome[F, Cause[E], A]] =
    interruptedHandle.get.map(toOutcomeCauseOtherFiber(_)(ZIO.succeed(_), exit))
}

private class ZioSemigroupK[R, E] extends SemigroupK[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def combineK[A](a: F[A], b: F[A]): F[A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    a orElse b
  }
}

private class ZioMonoidK[R, E](implicit monoid: Monoid[E]) extends MonoidK[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def empty[A]: F[A] =
    ZIO.fail(monoid.empty)(CoreTracer.newTrace)

  override final def combineK[A](a: F[A], b: F[A]): F[A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    a.catchAll(e1 => b.catchAll(e2 => ZIO.fail(monoid.combine(e1, e2))))
  }
}

private class ZioBifunctor[R] extends Bifunctor[ZIO[R, _, _]] {
  type F[A, B] = ZIO[R, A, B]

  override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fab.mapBoth(f, g)
  }
}

private class ZioParallel[R, E](final override implicit val monad: Monad[ZIO[R, E, _]]) extends Parallel[ZIO[R, E, _]] {
  type G[A] = ZIO[R, E, A]
  type F[A] = ParallelF[G, A]

  final override val applicative: Applicative[F] =
    new ZioParApplicative[R, E]

  final override val sequential: F ~> G = new (F ~> G) {
    def apply[A](fa: F[A]): G[A] = ParallelF.value(fa)
  }

  final override val parallel: G ~> F = new (G ~> F) {
    def apply[A](fa: G[A]): F[A] = ParallelF(fa)
  }
}

private class ZioParApplicative[R, E] extends CommutativeApplicative[ParallelF[ZIO[R, E, _], _]] {
  type G[A] = ZIO[R, E, A]
  type F[A] = ParallelF[G, A]

  final override def pure[A](x: A): F[A] =
    ParallelF[G, A](ZIO.succeed(x))

  final override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).interruptible.zipWithPar(ParallelF.value(fb).interruptible)(f))
  }

  final override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    map2(ff, fa)(_ apply _)

  final override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = {
    implicit def trace: Trace = CoreTracer.newTrace

    ParallelF(ParallelF.value(fa).interruptible.zipPar(ParallelF.value(fb).interruptible))
  }

  final override def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).map(f))
  }

  final override val unit: F[Unit] =
    ParallelF[G, Unit](ZIO.unit)
}

private class ZioSemigroup[R, E, A](implicit semigroup: Semigroup[A]) extends Semigroup[ZIO[R, E, A]] {
  type T = ZIO[R, E, A]

  override final def combine(x: T, y: T): T = {
    implicit def trace: Trace = CoreTracer.newTrace

    x.zipWith(y)(semigroup.combine)
  }
}

private class ZioMonoid[R, E, A](implicit monoid: Monoid[A]) extends ZioSemigroup[R, E, A] with Monoid[ZIO[R, E, A]] {
  override final val empty: T =
    ZIO.succeed(monoid.empty)
}

private class ZioParSemigroup[R, E, A](implicit semigroup: CommutativeSemigroup[A])
    extends CommutativeSemigroup[ParallelF[ZIO[R, E, _], A]] {

  type T = ParallelF[ZIO[R, E, _], A]

  override final def combine(x: T, y: T): T = {
    implicit def trace: Trace = CoreTracer.newTrace

    ParallelF(ParallelF.value(x).zipWithPar(ParallelF.value(y))(semigroup.combine))
  }
}

private class ZioParMonoid[R, E, A](implicit monoid: CommutativeMonoid[A])
    extends ZioParSemigroup[R, E, A]
    with CommutativeMonoid[ParallelF[ZIO[R, E, _], A]] {

  override final val empty: T =
    ParallelF[ZIO[R, E, _], A](ZIO.succeed(monoid.empty))
}

private class ZioLiftIO[R](implicit runtime: IORuntime) extends LiftIO[RIO[R, _]] {
  override final def liftIO[A](ioa: CIO[A]): RIO[R, A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    ZIO.asyncInterrupt { k =>
      val (result, cancel) = ioa.unsafeToFutureCancelable()
      k(ZIO.fromFuture(_ => result))
      Left(ZIO.fromFuture(_ => cancel()).orDie)
    }
  }
}
