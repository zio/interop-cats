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
import zio.{ Fiber, Ref as ZRef, ZEnvironment }
import zio.*
import zio.Clock.{ currentTime, nanoTime }
import zio.Duration

import zio.internal.stacktracer.InteropTracer
import zio.internal.stacktracer.{ Tracer => CoreTracer }

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.*

object catz extends CatsEffectPlatform {
  object core extends CatsPlatform
  object mtl  extends CatsMtlPlatform

  /**
   * `import zio.interop.catz.implicits._` brings in the default Runtime in order to
   * summon Cats Effect typeclasses without the ceremony of
   *
   * {{{
   * ZIO.runtime[Any].flatMap { implicit runtime =>
   *  implicit val asyncTask: Async[Task] = implicitly
   *  ...
   * }
   * }}}
   */
  object implicits {
    implicit val rts: Runtime[Any] = Runtime.default
  }
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

  implicit final def temporalInstance[R, E]: GenTemporal[ZIO[R, E, _], E] =
    temporalInstance0.asInstanceOf[GenTemporal[ZIO[R, E, _], E]]

  implicit final def concurrentInstance[R, E]: GenConcurrent[ZIO[R, E, _], E] =
    concurrentInstance0.asInstanceOf[GenConcurrent[ZIO[R, E, _], E]]

  implicit final def asyncRuntimeInstance[E](implicit runtime: Runtime[Any]): Async[Task] =
    new ZioRuntimeAsync

  implicit final def temporalRuntimeInstance[E](implicit runtime: Runtime[Any]): GenTemporal[IO[E, _], E] =
    new ZioRuntimeTemporal[E]

  private[this] val asyncInstance0: Async[Task] =
    new ZioAsync

  private[this] val temporalInstance0: Temporal[Task] =
    new ZioTemporal

  private[this] val concurrentInstance0: Concurrent[Task] =
    new ZioConcurrent[Any, Throwable]
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
    new ZioMonadError[Any, Throwable]
}

private class ZioDefer[R, E] extends Defer[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def defer[A](fa: => F[A]): F[A] = {
    val byName: () => F[A] = () => fa
    ZIO.suspendSucceed(fa)(InteropTracer.newTrace(byName))
  }
}

private class ZioConcurrent[R, E] extends ZioMonadError[R, E] with GenConcurrent[ZIO[R, E, _], E] {

  private def toPoll(restore: ZIO.InterruptStatusRestore) = new Poll[ZIO[R, E, _]] {
    override def apply[T](fa: ZIO[R, E, T]): ZIO[R, E, T] = restore(fa)(CoreTracer.newTrace)
  }

  private def toFiber[A](fiber: Fiber[E, A])(implicit trace: ZTraceElement) = new effect.Fiber[F, E, A] {
    override final val cancel: F[Unit]           = fiber.interrupt.unit
    override final val join: F[Outcome[F, E, A]] = fiber.await.map(toOutcome)
  }

  private def fiberFailure(error: E) =
    FiberFailure(Cause.fail(error))

  override def ref[A](a: A): F[effect.Ref[F, A]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZRef.make(a).map(new ZioRef(_))
  }

  override def deferred[A]: F[Deferred[F, A]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    Promise.make[E, A].map(new ZioDeferred(_))
  }

  override final def start[A](fa: F[A]): F[effect.Fiber[F, E, A]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.interruptible.forkDaemon.map(toFiber)
  }

  override def never[A]: F[A] =
    ZIO.never(CoreTracer.newTrace)

  override final def cede: F[Unit] =
    ZIO.yieldNow(CoreTracer.newTrace)

  override final def forceR[A, B](fa: F[A])(fb: F[B]): F[B] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.foldCauseZIO(cause => if (cause.isInterrupted) ZIO.failCause(cause) else fb, _ => fb)
  }

  override final def uncancelable[A](body: Poll[F] => F[A]): F[A] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(body)

    ZIO.uninterruptibleMask(body.compose(toPoll))
  }

  override final def canceled: F[Unit] =
    ZIO.interrupt(CoreTracer.newTrace)

  override final def onCancel[A](fa: F[A], fin: F[Unit]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.onError(cause => fin.orDieWith(fiberFailure).unless(cause.isFailure))
  }

  override final def memoize[A](fa: F[A]): F[F[A]] =
    fa.memoize(CoreTracer.newTrace)

  override final def racePair[A, B](fa: F[A], fb: F[B]) = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    (fa.interruptible raceWith fb.interruptible)(
      (exit, fiber) => ZIO.succeedNow(Left((toOutcome(exit), toFiber(fiber)))),
      (exit, fiber) => ZIO.succeedNow(Right((toFiber(fiber), toOutcome(exit))))
    )
  }

  override final def both[A, B](fa: F[A], fb: F[B]): F[(A, B)] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.interruptible zipPar fb.interruptible
  }

  override final def guarantee[A](fa: F[A], fin: F[Unit]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.ensuring(fin.orDieWith(fiberFailure))
  }

  override final def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(use)

    acquire.acquireReleaseWith(release.andThen(_.orDieWith(fiberFailure)), use)
  }

  override val unique: F[Unique.Token] =
    ZIO.succeed(new Unique.Token)(CoreTracer.newTrace)
}

private final class ZioDeferred[R, E, A](promise: Promise[E, A]) extends Deferred[ZIO[R, E, _], A] {
  type F[T] = ZIO[R, E, T]

  override val get: F[A] =
    promise.await(CoreTracer.newTrace)

  override def complete(a: A): F[Boolean] =
    promise.succeed(a)(CoreTracer.newTrace)

  override val tryGet: F[Option[A]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    promise.isDone.flatMap {
      case true  => get.asSome
      case false => ZIO.none
    }
  }
}

private final class ZioRef[R, E, A](ref: ZRef[A]) extends effect.Ref[ZIO[R, E, _], A] {
  type F[T] = ZIO[R, E, T]

  override def access: F[(A, A => F[Boolean])] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    get.map { current =>
      val called                   = new AtomicBoolean(false)
      def setter(a: A): F[Boolean] =
        ZIO.suspendSucceed {
          if (called.getAndSet(true)) {
            ZIO.succeedNow(false)
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
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    update(f).as(true)
  }

  override def tryModify[B](f: A => (A, B)): F[Option[B]] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    modify(f).asSome
  }

  override def update(f: A => A): F[Unit] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ref.update(f)
  }

  override def modify[B](f: A => (A, B)): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ref.modify(f(_).swap)
  }

  override def tryModifyState[B](state: State[A, B]): F[Option[B]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    modifyState(state).asSome
  }

  override def modifyState[B](state: State[A, B]): F[B] =
    modify(state.run(_).value)

  override def set(a: A): F[Unit] =
    ref.set(a)(CoreTracer.newTrace)

  override def get: F[A] =
    ref.get(CoreTracer.newTrace)
}

private class ZioTemporal[R, E] extends ZioConcurrent[R, E] with GenTemporal[ZIO[R, E, _], E] {

  override final def sleep(time: FiniteDuration): F[Unit] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZIO.sleep(Duration.fromScala(time))
  }

  override final val monotonic: F[FiniteDuration] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    nanoTime.map(FiniteDuration(_, NANOSECONDS))
  }

  override final val realTime: F[FiniteDuration] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    currentTime(MILLISECONDS).map(FiniteDuration(_, MILLISECONDS))
  }
}

private class ZioRuntimeTemporal[E](implicit runtime: Runtime[Any])
    extends ZioConcurrent[Any, E]
    with GenTemporal[IO[E, _], E] {

  private[this] val underlying: GenTemporal[ZIO[Any, E, _], E] = new ZioTemporal[Any, E]
  private[this] val clock: ZEnvironment[Any]                   = runtime.environment

  override final def sleep(time: FiniteDuration): F[Unit] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.sleep(time).provideEnvironment(clock)
  }

  override final val monotonic: F[FiniteDuration] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.monotonic.provideEnvironment(clock)
  }

  override final val realTime: F[FiniteDuration] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.realTime.provideEnvironment(clock)
  }
}

private class ZioRuntimeAsync(implicit runtime: Runtime[Any]) extends ZioRuntimeTemporal[Throwable] with Async[Task] {

  private[this] val underlying: Async[RIO[Any, _]] = new ZioAsync[Any]
  private[this] val environment: ZEnvironment[Any] = runtime.environment

  override final def evalOn[A](fa: F[A], ec: ExecutionContext): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.evalOn(fa, ec).provideEnvironment(environment)
  }

  override final val executionContext: F[ExecutionContext] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.executionContext.provideEnvironment(environment)
  }

  override final val unique: F[Unique.Token] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.unique.provideEnvironment(environment)
  }

  override final def cont[K, Q](body: Cont[F, K, Q]): F[Q] =
    Async.defaultCont(body)(this)

  override final def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = {
    val byName: () => A               = () => thunk
    implicit def trace: ZTraceElement = InteropTracer.newTrace(byName)

    underlying.suspend(hint)(thunk).provideEnvironment(environment)
  }

  override final def delay[A](thunk: => A): F[A] = {
    val byName: () => A               = () => thunk
    implicit def trace: ZTraceElement = InteropTracer.newTrace(byName)

    underlying.delay(thunk).provideEnvironment(environment)
  }

  override final def defer[A](thunk: => F[A]): F[A] = {
    val byName: () => F[A]            = () => thunk
    implicit def trace: ZTraceElement = InteropTracer.newTrace(byName)

    underlying.defer(thunk).provideEnvironment(environment)
  }

  override final def blocking[A](thunk: => A): F[A] = {
    val byName: () => A               = () => thunk
    implicit def trace: ZTraceElement = InteropTracer.newTrace(byName)

    underlying.blocking(thunk).provideEnvironment(environment)
  }

  override final def interruptible[A](thunk: => A): F[A] = {
    val byName: () => A               = () => thunk
    implicit def trace: ZTraceElement = InteropTracer.newTrace(byName)

    underlying.interruptible(thunk).provideEnvironment(environment)
  }

  override final def async[A](k: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(k)

    underlying.async(k).provideEnvironment(environment)
  }

  override final def async_[A](k: (Either[Throwable, A] => Unit) => Unit): F[A] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(k)

    underlying.async_(k).provideEnvironment(environment)
  }

  override final def fromFuture[A](fut: F[Future[A]]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    underlying.fromFuture(fut).provideEnvironment(environment)
  }

  override final def never[A]: F[A] =
    ZIO.never(CoreTracer.newTrace)
}

private class ZioMonadError[R, E] extends MonadError[ZIO[R, E, _], E] with StackSafeMonad[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def pure[A](a: A): F[A] =
    ZIO.succeedNow(a)

  override final def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.map(f)
  }

  override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.flatMap(f)
  }

  override final def flatTap[A, B](fa: F[A])(f: A => F[B]): F[A] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.tap(f)
  }

  override final def widen[A, B >: A](fa: F[A]): F[B] =
    fa

  override final def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.zipWith(fb)(f)
  }

  override final def as[A, B](fa: F[A], b: B): F[B] =
    fa.as(b)(CoreTracer.newTrace)

  override final def whenA[A](cond: Boolean)(f: => F[A]): F[Unit] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ZIO.when(cond)(f).unit
  }

  override final def unit: F[Unit] =
    ZIO.unit

  override final def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.catchAll(f)
  }

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.catchSome(pf)
  }

  override final def raiseError[A](e: E): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZIO.fail(e)
  }

  override final def attempt[A](fa: F[A]): F[Either[E, A]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.either
  }

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[E, E]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.mapError(pf.orElse { case error => error })
  }
}

private class ZioSemigroupK[R, E] extends SemigroupK[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def combineK[A](a: F[A], b: F[A]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    a orElse b
  }
}

private class ZioMonoidK[R, E](implicit monoid: Monoid[E]) extends MonoidK[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def empty[A]: F[A] =
    ZIO.fail(monoid.empty)(CoreTracer.newTrace)

  override final def combineK[A](a: F[A], b: F[A]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    a.catchAll(e1 => b.catchAll(e2 => ZIO.fail(monoid.combine(e1, e2))))
  }
}

private class ZioBifunctor[R] extends Bifunctor[ZIO[R, _, _]] {
  type F[A, B] = ZIO[R, A, B]

  override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

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
    ParallelF[G, A](ZIO.succeedNow(x))

  final override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).interruptible.zipWithPar(ParallelF.value(fb).interruptible)(f))
  }

  final override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    map2(ff, fa)(_ apply _)

  final override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ParallelF(ParallelF.value(fa).interruptible.zipPar(ParallelF.value(fb).interruptible))
  }

  final override def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).map(f))
  }

  final override val unit: F[Unit] =
    ParallelF[G, Unit](ZIO.unit)
}

private class ZioSemigroup[R, E, A](implicit semigroup: Semigroup[A]) extends Semigroup[ZIO[R, E, A]] {
  type T = ZIO[R, E, A]

  override final def combine(x: T, y: T): T = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    x.zipWith(y)(semigroup.combine)
  }
}

private class ZioMonoid[R, E, A](implicit monoid: Monoid[A]) extends ZioSemigroup[R, E, A] with Monoid[ZIO[R, E, A]] {
  override final val empty: T =
    ZIO.succeedNow(monoid.empty)
}

private class ZioParSemigroup[R, E, A](implicit semigroup: CommutativeSemigroup[A])
    extends CommutativeSemigroup[ParallelF[ZIO[R, E, _], A]] {

  type T = ParallelF[ZIO[R, E, _], A]

  override final def combine(x: T, y: T): T = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ParallelF(ParallelF.value(x).zipWithPar(ParallelF.value(y))(semigroup.combine))
  }
}

private class ZioParMonoid[R, E, A](implicit monoid: CommutativeMonoid[A])
    extends ZioParSemigroup[R, E, A]
    with CommutativeMonoid[ParallelF[ZIO[R, E, _], A]] {

  override final val empty: T =
    ParallelF[ZIO[R, E, _], A](ZIO.succeedNow(monoid.empty))
}

private class ZioLiftIO[R](implicit runtime: IORuntime) extends LiftIO[RIO[R, _]] {
  override final def liftIO[A](ioa: CIO[A]): RIO[R, A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZIO.asyncInterrupt { k =>
      val (result, cancel) = ioa.unsafeToFutureCancelable()
      k(ZIO.fromFuture(_ => result))
      Left(ZIO.fromFuture(_ => cancel()).orDie)
    }
  }
}
