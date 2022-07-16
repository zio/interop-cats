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

import cats.effect.{ Concurrent, ContextShift, ExitCase }
import cats.{ effect, _ }
import zio._
import zio.Clock.{ ClockLive => zioClock }
import zio.internal.stacktracer.{ Tracer => CoreTracer }
import zio.internal.stacktracer.InteropTracer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS, TimeUnit }
import zio.ZIOAppDefault

object catz extends CatsEffectPlatform {
  object core extends CatsPlatform
  object mtl  extends CatsMtlPlatform
}

abstract class CatsEffectPlatform
    extends CatsEffectInstances
    with CatsChunkInstances
    with CatsNonEmptyChunkInstances
    with CatsScopedSyntax
    with CatsConcurrentEffectSyntax
    with CatsClockSyntax {

  val console: interop.console.cats.type = interop.console.cats

  trait CatsApp extends ZIOAppDefault {
    override implicit val runtime: Runtime[Any] = super.runtime
  }

  object implicits {
    implicit final def ioTimer[R, E]: effect.Timer[ZIO[R, E, *]] = ioTimer0.asInstanceOf[effect.Timer[ZIO[R, E, *]]]

    private[this] val ioTimer0: effect.Timer[UIO] =
      zioClock.toTimer
  }

}

abstract class CatsPlatform extends CatsInstances with CatsChunkInstances with CatsNonEmptyChunkInstances

abstract class CatsEffectInstances extends CatsInstances with CatsEffectInstances1 {

  implicit final def zioContextShift[R, E]: ContextShift[ZIO[R, E, *]] =
    zioContextShift0.asInstanceOf[ContextShift[ZIO[R, E, *]]]

  implicit final def zioTimer[R <: Clock, E]: effect.Timer[ZIO[R, E, *]] =
    zioTimer0.asInstanceOf[effect.Timer[ZIO[R, E, *]]]

  implicit final def taskEffectInstance[R](implicit runtime: Runtime[R]): effect.ConcurrentEffect[RIO[R, *]] =
    new CatsConcurrentEffect[R](runtime)

  private[this] final val zioContextShift0: ContextShift[ZIO[Any, Any, *]] =
    new ContextShift[ZIO[Any, Any, *]] {
      override final def shift: ZIO[Any, Any, Unit] = ZIO.yieldNow(CoreTracer.newTrace)
      override final def evalOn[A](ec: ExecutionContext)(fa: ZIO[Any, Any, A]): ZIO[Any, Any, A] =
        fa.onExecutionContext(ec)(CoreTracer.newTrace)
    }

  private[this] final val zioTimer0: effect.Timer[ZIO[Clock, Any, *]] = new effect.Timer[ZIO[Clock, Any, *]] {
    override final def clock: effect.Clock[ZIO[Clock, Any, *]] = zioCatsClock0
    override final def sleep(duration: FiniteDuration): ZIO[Clock, Any, Unit] =
      zio.Clock.sleep(zio.Duration.fromNanos(duration.toNanos))(CoreTracer.newTrace)
  }

  private[this] final val zioCatsClock0: effect.Clock[ZIO[Clock, Any, *]] =
    new effect.Clock[ZIO[Clock, Any, *]] {
      override final def monotonic(unit: TimeUnit): ZIO[Clock, Any, Long] = {
        implicit def trace: Trace = CoreTracer.newTrace

        zio.Clock.nanoTime.map(unit.convert(_, NANOSECONDS))
      }
      override final def realTime(unit: TimeUnit): ZIO[Clock, Any, Long] =
        zio.Clock.currentTime(unit)(CoreTracer.newTrace)
    }

}

sealed trait CatsEffectInstances1 {
  implicit final def taskConcurrentInstance[R]: effect.Concurrent[RIO[R, *]] =
    taskConcurrentInstance0.asInstanceOf[effect.Concurrent[RIO[R, *]]]

  private[this] final val taskConcurrentInstance0: effect.Concurrent[RIO[Any, *]] = new CatsConcurrent[Any]
}

abstract class CatsInstances extends CatsInstances1 {

  implicit final def monoidKInstance[R, E: Monoid]: MonoidK[ZIO[R, E, *]] =
    new CatsMonoidK[R, E]

  implicit final def deferInstance[R, E]: Defer[ZIO[R, E, *]] =
    new CatsDefer[R, E]

  implicit final def bifunctorInstance[R]: Bifunctor[ZIO[R, *, *]] =
    bifunctorInstance0.asInstanceOf[Bifunctor[ZIO[R, *, *]]]

  private[this] val bifunctorInstance0: Bifunctor[ZIO[Any, *, *]] = new CatsBifunctor
}

sealed abstract class CatsInstances1 extends CatsInstances2 {

  implicit final def parallelInstance[R, E]: Parallel.Aux[ZIO[R, E, *], ParIO[R, E, *]] =
    parallelInstance0.asInstanceOf[Parallel.Aux[ZIO[R, E, *], ParIO[R, E, *]]]

  implicit final def commutativeApplicativeInstance[R, E]: CommutativeApplicative[ParIO[R, E, *]] =
    commutativeApplicativeInstance0.asInstanceOf[CommutativeApplicative[ParIO[R, E, *]]]

  implicit final def semigroupKInstance[R, E: Semigroup]: SemigroupK[ZIO[R, E, *]] =
    new CatsSemigroupK[R, E]

  private[this] final val parallelInstance0: Parallel.Aux[ZIO[Any, Any, *], ParIO[Any, Any, *]] =
    new CatsParallel[Any, Any](monadErrorInstance)

  private[this] final val commutativeApplicativeInstance0: CommutativeApplicative[ParIO[Any, Any, *]] =
    new CatsParApplicative[Any, Any]
}

sealed abstract class CatsInstances2 {

  implicit final def monadErrorInstance[R, E]: MonadError[ZIO[R, E, *], E] =
    monadErrorInstance0.asInstanceOf[MonadError[ZIO[R, E, *], E]]

  implicit final def semigroupKLossyInstance[R, E]: SemigroupK[ZIO[R, E, *]] =
    semigroupKLossyInstance0.asInstanceOf[SemigroupK[ZIO[R, E, *]]]

  private[this] final val monadErrorInstance0: MonadError[ZIO[Any, Any, *], Any] =
    new CatsMonadError[Any, Any]

  private[this] final val semigroupKLossyInstance0: SemigroupK[ZIO[Any, Any, *]] =
    new CatsSemigroupKLossy[Any, Any]
}

private class CatsDefer[R, E] extends Defer[ZIO[R, E, *]] {
  override def defer[A](fa: => ZIO[R, E, A]): ZIO[R, E, A] = {
    val byName: () => ZIO[R, E, A] = () => fa
    ZIO.suspendSucceed(fa)(InteropTracer.newTrace(byName))
  }
}

private class CatsConcurrentEffect[R](rts: Runtime[R])
    extends CatsConcurrent[R]
    with effect.ConcurrentEffect[RIO[R, *]] {

  override final def runAsync[A](fa: RIO[R, A])(
    cb: Either[Throwable, A] => effect.IO[Unit]
  ): effect.SyncIO[Unit] = {
    implicit def trace: Trace = InteropTracer.newTrace(cb)

    effect.SyncIO {
      Unsafe.unsafeCompat { implicit u =>
        val fiber = rts.unsafe.fork(fa.exit)
        fiber.unsafe.addObserver { exit =>
          cb(exit.getOrThrowFiberFailure().toEither).unsafeRunAsync(_ => ())
        }
      }
    }
  }

  override final def runCancelable[A](fa: RIO[R, A])(
    cb: Either[Throwable, A] => effect.IO[Unit]
  ): effect.SyncIO[effect.CancelToken[RIO[R, *]]] = {
    implicit def trace: Trace = InteropTracer.newTrace(cb)

    effect.SyncIO {
      Unsafe.unsafeCompat { implicit u =>
        rts.unsafe.run {
          ZIO
            .acquireReleaseExitWith(ZIO.descriptor)(
              (descriptor, exit: Exit[Throwable, A]) =>
                ZIO.succeed {
                  exit match {
                    case Exit.Failure(cause) if !cause.interruptors.forall(_ == descriptor.id) => ()
                    case _ =>
                      effect.IO.suspend(cb(exit.toEither)).unsafeRunAsync(_ => ())
                  }
                }
            )(_ => fa)
            .interruptible
            .forkDaemon
            .map(_.interrupt.unit)
        }.getOrThrowFiberFailure()
      }
    }
  }

  override final def toIO[A](fa: RIO[R, A]): effect.IO[A] =
    effect.ConcurrentEffect.toIOFromRunCancelable(fa)(this)
}

private class CatsConcurrent[R] extends CatsMonadError[R, Throwable] with Concurrent[RIO[R, *]] {

  private[this] final def toFiber[A](f: Fiber[Throwable, A]): effect.Fiber[RIO[R, *], A] =
    new effect.Fiber[RIO[R, *], A] {
      implicit def trace: Trace               = CoreTracer.newTrace
      override final val cancel: RIO[R, Unit] = f.interrupt.unit
      override final val join: RIO[R, A]      = f.join
    }

  override final def liftIO[A](ioa: effect.IO[A]): RIO[R, A] =
    Concurrent.liftIO(ioa)(this)

  override final def cancelable[A](k: (Either[Throwable, A] => Unit) => effect.CancelToken[RIO[R, *]]): RIO[R, A] = {
    implicit def trace: Trace = InteropTracer.newTrace(k)

    ZIO.asyncInterrupt { kk =>
      val token = k(kk apply _.fold(ZIO.fail(_), ZIO.succeedNow))
      Left(token.orDie)
    }
  }

  override final def start[A](fa: RIO[R, A]): RIO[R, effect.Fiber[RIO[R, *], A]] = {
    implicit def trace: Trace = CoreTracer.newTrace
    val self                  = fa.interruptible
    ZIO
      .withFiberRuntime[R, Nothing, Fiber.Runtime[Throwable, A]] { (fiberState, status) =>
        ZIO.succeedNow {
          val fiber = forkUnstarted(trace, self, fiberState, status.runtimeFlags)(Unsafe.unsafe)
          fiber.setFiberRef(FiberRef.interruptedCause, Cause.empty)(Unsafe.unsafe)
          fiber.startFork(self)(Unsafe.unsafe)
          fiber
        }
      }
      .daemonChildren
      .map(toFiber(_))
  }

  override final def racePair[A, B](
    fa: RIO[R, A],
    fb: RIO[R, B]
  ): RIO[R, Either[(A, effect.Fiber[RIO[R, *], B]), (effect.Fiber[RIO[R, *], A], B)]] = {
    implicit def trace: Trace = CoreTracer.newTrace

    def run[C](fc: RIO[R, C]): ZIO[R, Throwable, C] =
      fc.interruptible

    (run(fa) raceWith run(fb))(
      { case (l, f) => l.foldExit(f.interrupt *> ZIO.failCause(_), ZIO.succeedNow).map(lv => Left((lv, toFiber(f)))) },
      { case (r, f) => r.foldExit(f.interrupt *> ZIO.failCause(_), ZIO.succeedNow).map(rv => Right((toFiber(f), rv))) }
    )
  }

  override final def never[A]: RIO[R, A] =
    ZIO.never(CoreTracer.newTrace)

  override final def async[A](k: (Either[Throwable, A] => Unit) => Unit): RIO[R, A] = {
    implicit def trace: Trace = InteropTracer.newTrace(k)

    ZIO.async(kk => k(kk apply _.fold(ZIO.fail(_), ZIO.succeedNow)))
  }

  override final def asyncF[A](k: (Either[Throwable, A] => Unit) => RIO[R, Unit]): RIO[R, A] = {
    implicit def trace: Trace = InteropTracer.newTrace(k)

    ZIO.asyncZIO(kk => k(kk apply _.fold(ZIO.fail(_), ZIO.succeedNow)).orDie)
  }

  override final def suspend[A](thunk: => RIO[R, A]): RIO[R, A] = {
    val byName: () => RIO[R, A] = () => thunk
    ZIO.suspend(thunk)(InteropTracer.newTrace(byName))
  }

  override final def delay[A](thunk: => A): RIO[R, A] = {
    val byName: () => A = () => thunk
    ZIO.attempt(thunk)(InteropTracer.newTrace(byName))
  }

  override final def bracket[A, B](acquire: RIO[R, A])(use: A => RIO[R, B])(release: A => RIO[R, Unit]): RIO[R, B] = {
    implicit def trace: Trace = InteropTracer.newTrace(use)

    ZIO.acquireReleaseWith(acquire)(release(_: A).orDie)(use)
  }

  override final def bracketCase[A, B](acquire: RIO[R, A])(use: A => RIO[R, B])(
    release: (A, ExitCase[Throwable]) => RIO[R, Unit]
  ): RIO[R, B] = {
    implicit def trace: Trace = InteropTracer.newTrace(release)

    ZIO.acquireReleaseExitWith(acquire)((a: A, exit: Exit[Throwable, B]) => release(a, exitToExitCase(exit)).orDie)(use)
  }

  override final def uncancelable[A](fa: RIO[R, A]): RIO[R, A] =
    fa.uninterruptible(CoreTracer.newTrace)

  override final def guarantee[A](fa: RIO[R, A])(finalizer: RIO[R, Unit]): RIO[R, A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    fa.ensuring(finalizer.orDie)
  }

  override final def continual[A, B](fa: RIO[R, A])(f: Either[Throwable, A] => RIO[R, B]): RIO[R, B] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ZIO.uninterruptibleMask(_(fa).either.flatMap(f))
  }

  private def forkUnstarted[R, E1, E2, A, B](
    trace: Trace,
    effect: ZIO[R, E1, A],
    parentFiber: internal.FiberRuntime[E2, B],
    parentRuntimeFlags: RuntimeFlags
  )(implicit unsafe: Unsafe): internal.FiberRuntime[E1, A] = {
    val childId         = FiberId.make(trace)
    val parentFiberRefs = parentFiber.getFiberRefs()
    val childFiberRefs  = parentFiberRefs.forkAs(childId)

    val childFiber = internal.FiberRuntime[E1, A](childId, childFiberRefs, parentRuntimeFlags)

    // Call the supervisor who can observe the fork of the child fiber
    val childEnvironment = childFiberRefs.getOrDefault(FiberRef.currentEnvironment)

    val supervisor = childFiber.getSupervisor()

    supervisor.onStart(
      childEnvironment,
      effect.asInstanceOf[ZIO[Any, Any, Any]],
      Some(parentFiber),
      childFiber
    )

    childFiber.addObserver(exit => supervisor.onEnd(exit, childFiber))

    val parentScope = parentFiber.getFiberRef(FiberRef.forkScopeOverride).getOrElse(parentFiber.scope)

    parentScope.add(parentRuntimeFlags, childFiber)(trace, unsafe)

    childFiber
  }
}

private class CatsMonadError[R, E] extends MonadError[ZIO[R, E, *], E] with StackSafeMonad[ZIO[R, E, *]] {
  override final def pure[A](a: A): ZIO[R, E, A]                          = ZIO.succeedNow(a)
  override final def map[A, B](fa: ZIO[R, E, A])(f: A => B): ZIO[R, E, B] = fa.map(f)(InteropTracer.newTrace(f))
  override final def flatMap[A, B](fa: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] =
    fa.flatMap(f)(InteropTracer.newTrace(f))
  override final def flatTap[A, B](fa: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, A] =
    fa.tap(f)(InteropTracer.newTrace(f))

  override final def widen[A, B >: A](fa: ZIO[R, E, A]): ZIO[R, E, B] = fa
  override final def map2[A, B, Z](fa: ZIO[R, E, A], fb: ZIO[R, E, B])(f: (A, B) => Z): ZIO[R, E, Z] =
    fa.zipWith(fb)(f)(InteropTracer.newTrace(f))
  override final def as[A, B](fa: ZIO[R, E, A], b: B): ZIO[R, E, B] = fa.as(b)(CoreTracer.newTrace)
  override final def whenA[A](cond: Boolean)(f: => ZIO[R, E, A]): ZIO[R, E, Unit] = {
    val byName: () => ZIO[R, E, A] = () => f
    implicit def tracer: Trace     = InteropTracer.newTrace(byName)

    ZIO.suspendSucceed(f).when(cond).unit
  }
  override final def unit: ZIO[R, E, Unit] = ZIO.unit

  override final def handleErrorWith[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fa.catchAll(f)
  }
  override final def recoverWith[A](fa: ZIO[R, E, A])(pf: PartialFunction[E, ZIO[R, E, A]]): ZIO[R, E, A] = {
    implicit def trace: Trace = InteropTracer.newTrace(pf)

    fa.catchSome(pf)
  }
  override final def raiseError[A](e: E): ZIO[R, E, A] = ZIO.fail(e)(CoreTracer.newTrace)

  override final def attempt[A](fa: ZIO[R, E, A]): ZIO[R, E, Either[E, A]] = {
    implicit def trace: Trace = CoreTracer.newTrace
    fa.either
  }
}

/** lossy, throws away errors using the "first success" interpretation of SemigroupK */
private class CatsSemigroupKLossy[R, E] extends SemigroupK[ZIO[R, E, *]] {
  override final def combineK[A](a: ZIO[R, E, A], b: ZIO[R, E, A]): ZIO[R, E, A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    a.catchAll { e1 =>
      b.catchAll { _ =>
        ZIO.fail(e1)
      }
    }
  }
}

private class CatsSemigroupK[R, E: Semigroup] extends SemigroupK[ZIO[R, E, *]] {
  override final def combineK[A](a: ZIO[R, E, A], b: ZIO[R, E, A]): ZIO[R, E, A] = {
    implicit def trace: Trace = CoreTracer.newTrace

    a.catchAll { e1 =>
      b.catchAll { e2 =>
        ZIO.fail(Semigroup[E].combine(e1, e2))
      }
    }
  }
}

private class CatsMonoidK[R, E: Monoid] extends CatsSemigroupK[R, E] with MonoidK[ZIO[R, E, *]] {
  override final def empty[A]: ZIO[R, E, A] = ZIO.fail(Monoid[E].empty)(CoreTracer.newTrace)
}

private class CatsBifunctor[R] extends Bifunctor[ZIO[R, *, *]] {
  override final def bimap[A, B, C, D](fab: ZIO[R, A, B])(f: A => C, g: B => D): ZIO[R, C, D] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    fab.mapBoth(f, g)
  }
}

private class CatsParallel[R, E](final override val monad: Monad[ZIO[R, E, *]]) extends Parallel[ZIO[R, E, *]] {

  final override type F[A] = ParIO[R, E, A]

  final override val applicative: Applicative[ParIO[R, E, *]] =
    new CatsParApplicative[R, E]

  final override val sequential: ParIO[R, E, *] ~> ZIO[R, E, *] =
    new (ParIO[R, E, *] ~> ZIO[R, E, *]) {
      def apply[A](fa: ParIO[R, E, A]): ZIO[R, E, A] = Par.unwrap(fa)
    }

  final override val parallel: ZIO[R, E, *] ~> ParIO[R, E, *] =
    new (ZIO[R, E, *] ~> ParIO[R, E, *]) {
      def apply[A](fa: ZIO[R, E, A]): ParIO[R, E, A] = Par(fa)
    }
}

private class CatsParApplicative[R, E] extends CommutativeApplicative[ParIO[R, E, *]] {

  final override def pure[A](x: A): ParIO[R, E, A] =
    Par(ZIO.succeedNow(x))

  final override def map2[A, B, Z](fa: ParIO[R, E, A], fb: ParIO[R, E, B])(f: (A, B) => Z): ParIO[R, E, Z] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    Par(Par.unwrap(fa).interruptible.zipWithPar(Par.unwrap(fb).interruptible)(f))
  }

  final override def ap[A, B](ff: ParIO[R, E, A => B])(fa: ParIO[R, E, A]): ParIO[R, E, B] = {
    implicit def trace: Trace = CoreTracer.newTrace

    Par(Par.unwrap(ff).interruptible.zipWithPar(Par.unwrap(fa).interruptible)(_(_)))
  }

  final override def product[A, B](fa: ParIO[R, E, A], fb: ParIO[R, E, B]): ParIO[R, E, (A, B)] = {
    implicit def trace: Trace = CoreTracer.newTrace

    Par(Par.unwrap(fa).interruptible.zipPar(Par.unwrap(fb).interruptible))
  }

  final override def map[A, B](fa: ParIO[R, E, A])(f: A => B): ParIO[R, E, B] =
    Par(Par.unwrap(fa).map(f)(InteropTracer.newTrace(f)))

  final override def unit: ParIO[R, E, Unit] =
    Par(ZIO.unit)
}
