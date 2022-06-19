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

import cats.arrow.ArrowChoice
import cats.data.State
import cats.effect.kernel.*
import cats.effect.unsafe.IORuntime
import cats.effect.{ IO as CIO, LiftIO }
import cats.kernel.{ CommutativeMonoid, CommutativeSemigroup }
import cats.effect
import cats.*
import zio.Fiber
import zio.*
import zio.clock.{ currentTime, nanoTime, Clock }
import zio.duration.Duration

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.*

object catz extends CatsEffectPlatform {
  object core extends CatsPlatform
  object mtl  extends CatsMtlPlatform

  /**
   * `import zio.interop.catz.implicits._` brings in the default Runtime in order to
   * summon Cats Effect typeclasses without the ceremony of
   *
   * {{{
   * ZIO.runtime[Clock with Blocking].flatMap { implicit runtime =>
   *  implicit val asyncTask: Async[Task] = implicitly
   *  ...
   * }
   * }}}
   */
  object implicits {
    implicit val rts: Runtime[Clock & CBlocking] = Runtime.default
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

  trait CatsApp extends App {
    implicit val runtime: Runtime[ZEnv] = this
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

  implicit final def asyncInstance[R <: Clock & CBlocking]: Async[RIO[R, _]] =
    asyncInstance0.asInstanceOf[Async[RIO[R, _]]]

  implicit final def temporalInstance[R <: Clock]: GenTemporal[ZIO[R, Throwable, _], Throwable] =
    temporalInstance0.asInstanceOf[GenTemporal[ZIO[R, Throwable, _], Throwable]]

  implicit final def concurrentInstance[R]: GenConcurrent[ZIO[R, Throwable, _], Throwable] =
    concurrentInstance0.asInstanceOf[GenConcurrent[ZIO[R, Throwable, _], Throwable]]

  implicit final def asyncRuntimeInstance[R](implicit runtime: Runtime[Clock & CBlocking]): Async[RIO[R, _]] =
    new ZioRuntimeAsync(runtime.environment)

  implicit final def temporalRuntimeInstance[R](implicit
    runtime: Runtime[Clock]
  ): GenTemporal[ZIO[R, Throwable, _], Throwable] =
    new ZioRuntimeTemporal[R, Throwable, Throwable](runtime.environment) with ZioMonadErrorExitThrowable[R]

  private[this] val asyncInstance0: Async[RIO[Clock & CBlocking, _]] =
    new ZioAsync[Clock & CBlocking] with ZioBlockingEnvIdentity[Clock & CBlocking, Throwable]

  private[this] val temporalInstance0: Temporal[RIO[Clock, _]] =
    new ZioTemporal[Clock, Throwable, Throwable]
      with ZioClockEnvIdentity[Clock, Throwable]
      with ZioMonadErrorExitThrowable[Clock]

  private[this] val concurrentInstance0: Concurrent[Task] =
    new ZioConcurrent[Any, Throwable, Throwable] with ZioMonadErrorExitThrowable[Any]
}

sealed abstract class CatsEffectInstancesCause {

  implicit final def temporalInstanceCause[R <: Clock, E]: GenTemporal[ZIO[R, E, _], Cause[E]] =
    temporalInstance1.asInstanceOf[GenTemporal[ZIO[R, E, _], Cause[E]]]

  implicit final def concurrentInstanceCause[R, E]: GenConcurrent[ZIO[R, E, _], Cause[E]] =
    concurrentInstance1.asInstanceOf[GenConcurrent[ZIO[R, E, _], Cause[E]]]

  implicit final def temporalRuntimeInstanceCause[R, E](implicit
    runtime: Runtime[Clock]
  ): GenTemporal[ZIO[R, E, _], Cause[E]] =
    new ZioRuntimeTemporal[R, E, Cause[E]](runtime.environment) with ZioMonadErrorExitCause[R, E]

  private[this] val temporalInstance1: GenTemporal[ZIO[Clock, Any, _], Cause[Any]] =
    new ZioTemporal[Clock, Any, Cause[Any]] with ZioClockEnvIdentity[Clock, Any] with ZioMonadErrorExitCause[Clock, Any]

  private[this] val concurrentInstance1: GenConcurrent[ZIO[Any, Any, _], Cause[Any]] =
    new ZioConcurrent[Any, Any, Cause[Any]] with ZioMonadErrorExitCause[Any, Any]
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

  implicit final def rioArrowChoiceInstance: ArrowChoice[RIO] =
    arrowChoiceInstance0

  implicit final def contravariantInstance[E, A]: Contravariant[ZIO[_, E, A]] =
    contravariantInstance0.asInstanceOf[Contravariant[ZIO[_, E, A]]]

  private[this] val deferInstance0: Defer[UIO] =
    new ZioDefer[Any, Nothing]

  private[this] val bifunctorInstance0: Bifunctor[IO] =
    new ZioBifunctor[Any]

  private[this] val contravariantInstance0: Contravariant[RIO[_, Any]] =
    new ZioContravariant
}

sealed abstract class CatsZioInstances1 extends CatsZioInstances2 {

  implicit final def urioArrowChoiceInstance: ArrowChoice[URIO] =
    arrowChoiceInstance0.asInstanceOf[ArrowChoice[URIO]]

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

  implicit final def zioArrowChoiceInstance[E]: ArrowChoice[ZIO[_, E, _]] =
    arrowChoiceInstance0.asInstanceOf[ArrowChoice[ZIO[_, E, _]]]

  implicit final def monadErrorInstance[R, E]: MonadError[ZIO[R, E, _], E] =
    monadErrorInstance0.asInstanceOf[MonadError[ZIO[R, E, _], E]]

  protected[this] final val arrowChoiceInstance0: ArrowChoice[RIO] =
    new ZioArrowChoice

  private[this] val monadErrorInstance0: MonadError[Task, Throwable] =
    new ZioMonadError[Any, Throwable, Throwable] with ZioMonadErrorE[Any, Throwable]
}

private class ZioDefer[R, E] extends Defer[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def defer[A](fa: => F[A]): F[A] =
    ZIO.effectSuspendTotal(fa)
}

private abstract class ZioConcurrent[R, E, E1]
    extends ZioMonadErrorExit[R, E, E1]
    with GenConcurrent[ZIO[R, E, _], E1] {

  private def toFiber[A](fiber: Fiber[E, A]): effect.Fiber[F, E1, A] = new effect.Fiber[F, E1, A] {
    override final val cancel: F[Unit]            = fiber.interrupt.unit
    override final val join: F[Outcome[F, E1, A]] = fiber.await.map(exitToOutcome)
  }

  private def toThrowableOrFiberFailure(error: E): Throwable =
    error match {
      case t: Throwable => t
      case _            => FiberFailure(Cause.fail(error))
    }

  override def ref[A](a: A): F[effect.Ref[F, A]] =
    ZRef.make(a).map(new ZioRef(_))

  override def deferred[A]: F[Deferred[F, A]] =
    Promise.make[E, A].map(new ZioDeferred(_))

  override final def start[A](fa: F[A]): F[effect.Fiber[F, E1, A]] =
    fa.interruptible.forkDaemon.map(toFiber)

  override def never[A]: F[A] =
    ZIO.never

  override final def cede: F[Unit] =
    ZIO.yieldNow

  override final def forceR[A, B](fa: F[A])(fb: F[B]): F[B] =
    fa.foldCauseM(cause => if (cause.interrupted) ZIO.halt(cause) else fb, _ => fb)

  override final def uncancelable[A](body: Poll[F] => F[A]): F[A] =
    ZIO.uninterruptibleMask(restore => body(toPoll(restore)))

  override final def canceled: F[Unit] =
    ZIO.interrupt

  override final def onCancel[A](fa: F[A], fin: F[Unit]): F[A] =
    fa.onError(cause => fin.orDieWith(toThrowableOrFiberFailure).unless(cause.failed))

  override final def memoize[A](fa: F[A]): F[F[A]] =
    fa.memoize

  override final def racePair[A, B](
    fa: F[A],
    fb: F[B]
  ): ZIO[R, Nothing, Either[(Outcome[F, E1, A], effect.Fiber[F, E1, B]), (effect.Fiber[F, E1, A], Outcome[F, E1, B])]] =
    (fa.interruptible raceWith fb.interruptible)(
      (exit, fiber) => ZIO.succeedNow(Left((exitToOutcome(exit), toFiber(fiber)))),
      (exit, fiber) => ZIO.succeedNow(Right((toFiber(fiber), exitToOutcome(exit))))
    )

  override final def both[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    fa.interruptible zipPar fb.interruptible

  override final def guarantee[A](fa: F[A], fin: F[Unit]): F[A] =
    fa.ensuring(fin.orDieWith(toThrowableOrFiberFailure))

  override final def bracket[A, B](acquire: F[A])(use: A => F[B])(release: A => F[Unit]): F[B] =
    acquire.bracket(release.andThen(_.orDieWith(toThrowableOrFiberFailure)), use)

  override val unique: F[Unique.Token] =
    ZIO.effectTotal(new Unique.Token)
}

private final class ZioDeferred[R, E, A](promise: Promise[E, A]) extends Deferred[ZIO[R, E, _], A] {
  type F[T] = ZIO[R, E, T]

  override val get: F[A] =
    promise.await

  override def complete(a: A): F[Boolean] =
    promise.succeed(a)

  override val tryGet: F[Option[A]] =
    promise.isDone.flatMap {
      case true  => get.asSome
      case false => ZIO.none
    }
}

private final class ZioRef[R, E, A](ref: ERef[E, A]) extends effect.Ref[ZIO[R, E, _], A] {
  type F[T] = ZIO[R, E, T]

  override def access: F[(A, A => F[Boolean])] =
    get.map { current =>
      val called                   = new AtomicBoolean(false)
      def setter(a: A): F[Boolean] =
        ZIO.effectSuspendTotal {
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

  override def tryUpdate(f: A => A): F[Boolean] =
    update(f).as(true)

  override def tryModify[B](f: A => (A, B)): F[Option[B]] =
    modify(f).asSome

  override def update(f: A => A): F[Unit] =
    ref.update(f)

  override def modify[B](f: A => (A, B)): F[B] =
    ref.modify(f(_).swap)

  override def tryModifyState[B](state: State[A, B]): F[Option[B]] =
    modifyState(state).asSome

  override def modifyState[B](state: State[A, B]): F[B] =
    modify(state.run(_).value)

  override def set(a: A): F[Unit] =
    ref.set(a)

  override def get: F[A] =
    ref.get
}

private abstract class ZioTemporal[R, E, E1]
    extends ZioConcurrent[R, E, E1]
    with GenTemporal[ZIO[R, E, _], E1]
    with ZioClockEnv[R, E] {

  override final def sleep(time: FiniteDuration): F[Unit] =
    withClock(ZIO.sleep(Duration.fromScala(time)))

  override final val monotonic: F[FiniteDuration] =
    withClock(nanoTime.map(FiniteDuration(_, NANOSECONDS)))

  override final val realTime: F[FiniteDuration] =
    withClock(currentTime(MILLISECONDS).map(FiniteDuration(_, MILLISECONDS)))
}

private abstract class ZioRuntimeTemporal[R, E, E1](environment: Clock)
    extends ZioTemporal[R, E, E1]
    with ZioClockEnv[R, E] {

  override protected[this] def withClock[A](fa: ZIO[Clock, E, A]): ZIO[R, E, A] = fa.provide(environment)

}

private class ZioRuntimeAsync[R](environment: Clock & CBlocking) extends ZioAsync[R] with ZioBlockingEnv[R, Throwable] {

  override protected[this] def withClock[A](fa: RIO[Clock, A]): RIO[R, A] = fa.provide(environment)

  override protected[this] def withBlocking[A](fa: RIO[CBlocking, A]): RIO[R, A] =
    fa.provide(environment)(NeedsEnv.needsEnv)

}

private trait ZioClockEnv[R, E] extends Any {
  protected[this] def withClock[A](fa: ZIO[Clock, E, A]): ZIO[R, E, A]
}

private trait ZioBlockingEnv[R, E] extends ZioClockEnv[R, E] {
  protected[this] def withBlocking[A](fa: ZIO[CBlocking, E, A]): ZIO[R, E, A]
}

private trait ZioClockEnvIdentity[R <: Clock, E] extends ZioClockEnv[R, E] {
  override protected[this] def withClock[A](fa: ZIO[Clock, E, A]): ZIO[R, E, A] = fa
}

private trait ZioBlockingEnvIdentity[R <: Clock & CBlocking, E]
    extends ZioBlockingEnv[R, E]
    with ZioClockEnvIdentity[R, E] {
  override protected[this] def withBlocking[A](fa: ZIO[CBlocking, E, A]): ZIO[R, E, A] = fa
}

private abstract class ZioMonadError[R, E, E1] extends MonadError[ZIO[R, E, _], E1] {
  type F[A] = ZIO[R, E, A]

  override final def pure[A](a: A): F[A] =
    ZIO.succeedNow(a)

  override final def map[A, B](fa: F[A])(f: A => B): F[B] =
    fa.map(f)

  override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] =
    fa.flatMap(f)

  override final def flatTap[A, B](fa: F[A])(f: A => F[B]): F[A] =
    fa.tap(f)

  override final def widen[A, B >: A](fa: F[A]): F[B] =
    fa

  override final def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    fa.zipWith(fb)(f)

  override final def as[A, B](fa: F[A], b: B): F[B] =
    fa.as(b)

  override final def whenA[A](cond: Boolean)(f: => F[A]): F[Unit] =
    ZIO.when(cond)(f)

  override final def unit: F[Unit] =
    ZIO.unit

  override final def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = {
    def loop(a: A): F[B] = f(a).flatMap {
      case Left(a)  => loop(a)
      case Right(b) => ZIO.succeedNow(b)
    }

    ZIO.effectSuspendTotal(loop(a))
  }

}

private trait ZioMonadErrorE[R, E] extends ZioMonadError[R, E, E] {

  override final def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] =
    fa.catchAll(f)

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
    fa.catchSome(pf)

  override final def raiseError[A](e: E): F[A] =
    ZIO.fail(e)

  override final def attempt[A](fa: F[A]): F[Either[E, A]] =
    fa.either

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[E, E]): F[A] =
    fa.mapError(pf.orElse { case error => error })
}

private trait ZioMonadErrorCause[R, E] extends ZioMonadError[R, E, Cause[E]] {

  override final def handleErrorWith[A](fa: F[A])(f: Cause[E] => F[A]): F[A] =
//    fa.catchAllCause(f)
    fa.catchSomeCause {
      // pretend that we can't catch inner interrupt to satisfy `uncancelable canceled associates right over flatMap attempt`
      // law since we use a poor definition of `canceled=ZIO.interrupt` right now
      // https://github.com/zio/interop-cats/issues/503#issuecomment-1157101175=
      case c if !c.interrupted => f(c)
    }

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[Cause[E], F[A]]): F[A] =
//    fa.catchSomeCause(pf)
    fa.catchSomeCause(({ case c if !c.interrupted => c }: PartialFunction[Cause[E], Cause[E]]).andThen(pf))

  override final def raiseError[A](e: Cause[E]): F[A] =
    ZIO.halt(e)

  override final def attempt[A](fa: F[A]): F[Either[Cause[E], A]] =
//    fa.sandbox.attempt
    fa.map(Right(_)).catchSomeCause {
      case c if !c.interrupted => ZIO.succeedNow(Left(c))
    }

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[Cause[E], Cause[E]]): F[A] =
    fa.mapErrorCause(pf.orElse { case error => error })
}

private abstract class ZioMonadErrorExit[R, E, E1] extends ZioMonadError[R, E, E1] {
  protected def exitToOutcome[A](exit: Exit[E, A]): Outcome[F, E1, A]
}

private trait ZioMonadErrorExitThrowable[R]
    extends ZioMonadErrorExit[R, Throwable, Throwable]
    with ZioMonadErrorE[R, Throwable] {
  override protected def exitToOutcome[A](exit: Exit[Throwable, A]): Outcome[F, Throwable, A] = toOutcomeThrowable(exit)
}

private trait ZioMonadErrorExitCause[R, E] extends ZioMonadErrorExit[R, E, Cause[E]] with ZioMonadErrorCause[R, E] {
  override protected def exitToOutcome[A](exit: Exit[E, A]): Outcome[F, Cause[E], A] = toOutcomeCause(exit)
}

private class ZioSemigroupK[R, E] extends SemigroupK[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def combineK[A](a: F[A], b: F[A]): F[A] =
    a orElse b
}

private class ZioMonoidK[R, E](implicit monoid: Monoid[E]) extends MonoidK[ZIO[R, E, _]] {
  type F[A] = ZIO[R, E, A]

  override final def empty[A]: F[A] =
    ZIO.fail(monoid.empty)

  override final def combineK[A](a: F[A], b: F[A]): F[A] =
    a.catchAll(e1 => b.catchAll(e2 => ZIO.fail(monoid.combine(e1, e2))))
}

private class ZioBifunctor[R] extends Bifunctor[ZIO[R, _, _]] {
  type F[A, B] = ZIO[R, A, B]

  override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] =
    fab.mapBoth(f, g)
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

  final override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    ParallelF(ParallelF.value(fa).interruptible.zipWithPar(ParallelF.value(fb).interruptible)(f))

  final override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    map2(ff, fa)(_ apply _)

  final override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    ParallelF(ParallelF.value(fa).interruptible.zipPar(ParallelF.value(fb).interruptible))

  final override def map[A, B](fa: F[A])(f: A => B): F[B] =
    ParallelF(ParallelF.value(fa).map(f))

  final override val unit: F[Unit] =
    ParallelF[G, Unit](ZIO.unit)
}

private class ZioArrowChoice[E] extends ArrowChoice[ZIO[_, E, _]] {
  type F[A, B] = ZIO[A, E, B]

  final override def lift[A, B](f: A => B): F[A, B] =
    ZIO.fromFunction(f)

  final override def compose[A, B, C](f: F[B, C], g: F[A, B]): F[A, C] =
    f compose g

  final override def id[A]: F[A, A] =
    ZIO.identity[A]

  final override def dimap[A, B, C, D](fab: F[A, B])(f: C => A)(g: B => D): F[C, D] =
    fab.provideSome(f).map(g)

  final override def choose[A, B, C, D](f: F[A, C])(g: F[B, D]): F[Either[A, B], Either[C, D]] =
    f +++ g

  final override def first[A, B, C](fa: F[A, B]): F[(A, C), (B, C)] =
    fa *** ZIO.identity[C]

  final override def second[A, B, C](fa: F[A, B]): F[(C, A), (C, B)] =
    ZIO.identity[C] *** fa

  final override def split[A, B, C, D](f: F[A, B], g: F[C, D]): F[(A, C), (B, D)] =
    f *** g

  final override def merge[A, B, C](f: F[A, B], g: F[A, C]): F[A, (B, C)] =
    f zip g

  final override def lmap[A, B, C](fab: F[A, B])(f: C => A): F[C, B] =
    fab.provideSome(f)

  final override def rmap[A, B, C](fab: F[A, B])(f: B => C): F[A, C] =
    fab.map(f)

  final override def choice[A, B, C](f: F[A, C], g: F[B, C]): F[Either[A, B], C] =
    f ||| g
}

private class ZioContravariant[E, T] extends Contravariant[ZIO[_, E, T]] {
  type F[A] = ZIO[A, E, T]

  final override def contramap[A, B](fa: F[A])(f: B => A): F[B] =
    ZIO.accessM[B](b => fa.provide(f(b)))
}

private class ZioSemigroup[R, E, A](implicit semigroup: Semigroup[A]) extends Semigroup[ZIO[R, E, A]] {
  type T = ZIO[R, E, A]

  override final def combine(x: T, y: T): T =
    x.zipWith(y)(semigroup.combine)
}

private class ZioMonoid[R, E, A](implicit monoid: Monoid[A]) extends ZioSemigroup[R, E, A] with Monoid[ZIO[R, E, A]] {
  override final val empty: T =
    ZIO.succeedNow(monoid.empty)
}

private class ZioParSemigroup[R, E, A](implicit semigroup: CommutativeSemigroup[A])
    extends CommutativeSemigroup[ParallelF[ZIO[R, E, _], A]] {

  type T = ParallelF[ZIO[R, E, _], A]

  override final def combine(x: T, y: T): T =
    ParallelF(ParallelF.value(x).zipWithPar(ParallelF.value(y))(semigroup.combine))
}

private class ZioParMonoid[R, E, A](implicit monoid: CommutativeMonoid[A])
    extends ZioParSemigroup[R, E, A]
    with CommutativeMonoid[ParallelF[ZIO[R, E, _], A]] {

  override final val empty: T =
    ParallelF[ZIO[R, E, _], A](ZIO.succeedNow(monoid.empty))
}

private class ZioLiftIO[R](implicit runtime: IORuntime) extends LiftIO[RIO[R, _]] {
  override final def liftIO[A](ioa: CIO[A]): RIO[R, A] =
    ZIO.effectAsyncInterrupt { k =>
      val (result, cancel) = ioa.unsafeToFutureCancelable()
      k(ZIO.fromFuture(_ => result))
      Left(ZIO.fromFuture(_ => cancel()).orDie)
    }
}
