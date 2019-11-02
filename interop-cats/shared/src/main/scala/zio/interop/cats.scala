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
import zio.interop.test.CatsTestFunctions

import cats.arrow.ArrowChoice
import cats.effect.{ Concurrent, ContextShift, ExitCase }
import cats.{ effect, _ }
import zio._
import zio.clock.Clock

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS, TimeUnit }

object catz extends CatsEffectPlatform {
  object core extends CatsPlatform
  object mtl  extends CatsMtlPlatform
  object test extends CatsTestFunctions
}

abstract class CatsEffectPlatform
    extends CatsEffectInstances
    with CatsEffectZManagedInstances
    with CatsZManagedInstances
    with CatsZManagedSyntax {
  val console: interop.console.cats.type = interop.console.cats

  trait CatsApp extends App {
    implicit val runtime: Runtime[ZEnv] = this
  }

  object implicits {
    implicit def ioTimer[E]: effect.Timer[IO[E, *]] =
      new effect.Timer[IO[E, *]] {
        override def clock: effect.Clock[IO[E, *]] = new effect.Clock[IO[E, *]] {
          override def monotonic(unit: TimeUnit): IO[E, Long] =
            Clock.Live.clock.nanoTime.map(unit.convert(_, NANOSECONDS))

          override def realTime(unit: TimeUnit): IO[E, Long] =
            Clock.Live.clock.currentTime(unit)
        }

        override def sleep(duration: FiniteDuration): IO[E, Unit] =
          Clock.Live.clock.sleep(zio.duration.Duration.fromNanos(duration.toNanos))
      }
  }
}

abstract class CatsPlatform extends CatsInstances with CatsZManagedInstances

abstract class CatsEffectInstances extends CatsInstances with CatsEffectInstances1 {

  implicit def zioContextShift[R, E]: ContextShift[ZIO[R, E, *]] = new ContextShift[ZIO[R, E, *]] {
    override def shift: ZIO[R, E, Unit] =
      ZIO.yieldNow

    override def evalOn[A](ec: ExecutionContext)(fa: ZIO[R, E, A]): ZIO[R, E, A] =
      fa.on(ec)
  }

  implicit def zioTimer[R <: Clock, E]: effect.Timer[ZIO[R, E, *]] = new effect.Timer[ZIO[R, E, *]] {
    override def clock: effect.Clock[ZIO[R, E, *]] = new effect.Clock[ZIO[R, E, *]] {
      override def monotonic(unit: TimeUnit): ZIO[R, E, Long] =
        zio.clock.nanoTime.map(unit.convert(_, NANOSECONDS))

      override def realTime(unit: TimeUnit): ZIO[R, E, Long] =
        zio.clock.currentTime(unit)
    }

    override def sleep(duration: FiniteDuration): ZIO[R, E, Unit] =
      zio.clock.sleep(zio.duration.Duration.fromNanos(duration.toNanos))
  }

  implicit def taskEffectInstance[R](implicit runtime: Runtime[R]): effect.ConcurrentEffect[RIO[R, *]] =
    new CatsConcurrentEffect[R](runtime)

}

sealed trait CatsEffectInstances1 {
  implicit def taskConcurrentInstance[R]: effect.Concurrent[RIO[R, *]] =
    new CatsConcurrent[R]
}

abstract class CatsInstances extends CatsInstances1 {

  implicit def monoidKInstance[R, E: Monoid]: MonoidK[ZIO[R, E, *]] =
    new CatsMonoidK[R, E]

  implicit def bifunctorInstance[R]: Bifunctor[ZIO[R, *, *]] =
    new CatsBifunctor[R]

  implicit val rioArrowInstance: ArrowChoice[RIO] =
    new CatsArrow[Throwable]

  implicit val urioArrowInstance: ArrowChoice[URIO] =
    new CatsArrow[Nothing]

  implicit def zioArrowInstance[E]: ArrowChoice[ZIO[*, E, *]] =
    new CatsArrow[E]
}

sealed abstract class CatsInstances1 extends CatsInstances2 {

  implicit def parallelInstance[R, E]: Parallel.Aux[ZIO[R, E, *], ParIO[R, E, *]] =
    new CatsParallel[R, E](monadErrorInstance)

  implicit def commutativeApplicativeInstance[R, E]: CommutativeApplicative[ParIO[R, E, *]] =
    new CatsParApplicative[R, E]

  implicit def semigroupKInstance[R, E: Semigroup]: SemigroupK[ZIO[R, E, *]] =
    new CatsSemigroupK[R, E]
}

sealed abstract class CatsInstances2 {
  implicit def monadErrorInstance[R, E]: MonadError[ZIO[R, E, *], E] =
    new CatsMonadError[R, E]

  implicit def semigroupKLossyInstance[R, E]: SemigroupK[ZIO[R, E, *]] =
    new CatsSemigroupKLossy[R, E]
}

private class CatsConcurrentEffect[R](rts: Runtime[R])
    extends CatsConcurrent[R]
    with effect.ConcurrentEffect[RIO[R, *]]
    with effect.Effect[RIO[R, *]] {

  override final def runAsync[A](fa: RIO[R, A])(
    cb: Either[Throwable, A] => effect.IO[Unit]
  ): effect.SyncIO[Unit] =
    effect.SyncIO {
      rts.unsafeRunAsync(fa) { exit =>
        cb(exit.toEither).unsafeRunAsync(_ => ())
      }
    }

  override final def runCancelable[A](fa: RIO[R, A])(
    cb: Either[Throwable, A] => effect.IO[Unit]
  ): effect.SyncIO[effect.CancelToken[RIO[R, *]]] =
    effect.SyncIO {
      rts.unsafeRun {
        RIO.unit
          .bracketExit(
            (_, exit: Exit[Throwable, A]) =>
              RIO.effectTotal {
                effect.IO.suspend(cb(exit.toEither)).unsafeRunAsync(_ => ())
              },
            _ => fa
          )
          .interruptible
          .fork
          .map(_.interrupt.unit)
      }
    }

  override final def toIO[A](fa: RIO[R, A]): effect.IO[A] =
    effect.ConcurrentEffect.toIOFromRunCancelable(fa)(this)
}

private class CatsConcurrent[R] extends CatsEffect[R] with Concurrent[RIO[R, *]] {

  private[this] final def toFiber[A](f: Fiber[Throwable, A]): effect.Fiber[RIO[R, *], A] =
    new effect.Fiber[RIO[R, *], A] {
      override final val cancel: RIO[R, Unit] = f.interrupt.unit
      override final val join: RIO[R, A]      = f.join
    }

  override final def liftIO[A](ioa: effect.IO[A]): RIO[R, A] =
    Concurrent.liftIO(ioa)(this)

  override final def cancelable[A](k: (Either[Throwable, A] => Unit) => effect.CancelToken[RIO[R, *]]): RIO[R, A] =
    RIO.accessM { r =>
      RIO.effectAsyncInterrupt[R, A] { kk =>
        val token: effect.CancelToken[Task] = {
          k(e => kk(RIO.fromEither(e))).provide(r)
        }

        Left(token.provide(r).orDie)
      }
    }

  override final def race[A, B](fa: RIO[R, A], fb: RIO[R, B]): RIO[R, Either[A, B]] =
    fa.map(Left(_)).raceAttempt(fb.map(Right(_)))

  override final def start[A](fa: RIO[R, A]): RIO[R, effect.Fiber[RIO[R, *], A]] =
    RIO.interruptible(fa).fork.map(toFiber)

  override final def racePair[A, B](
    fa: RIO[R, A],
    fb: RIO[R, B]
  ): RIO[R, Either[(A, effect.Fiber[RIO[R, *], B]), (effect.Fiber[RIO[R, *], A], B)]] =
    (fa raceWith fb)(
      { case (l, f) => l.fold(f.interrupt *> RIO.halt(_), RIO.succeed).map(lv => Left((lv, toFiber(f)))) },
      { case (r, f) => r.fold(f.interrupt *> RIO.halt(_), RIO.succeed).map(rv => Right((toFiber(f), rv))) }
    )
}

private class CatsEffect[R] extends CatsMonadError[R, Throwable] with effect.Async[RIO[R, *]] {

  @inline final private[this] def exitToExitCase[A]: Exit[Throwable, A] => ExitCase[Throwable] = {
    case Exit.Success(_)                          => ExitCase.Completed
    case Exit.Failure(cause) if cause.interrupted => ExitCase.Canceled
    case Exit.Failure(cause) =>
      cause.failureOrCause match {
        case Left(t) => ExitCase.Error(t)
        case _       => ExitCase.Error(FiberFailure(cause))
      }
  }

  override final def never[A]: RIO[R, A] =
    RIO.never

  override final def async[A](k: (Either[Throwable, A] => Unit) => Unit): RIO[R, A] =
    RIO.effectAsync(kk => k(e => kk(RIO.fromEither(e))))

  override final def asyncF[A](k: (Either[Throwable, A] => Unit) => RIO[R, Unit]): RIO[R, A] =
    RIO.effectAsyncM(kk => k(e => kk(RIO.fromEither(e))).orDie)

  override final def suspend[A](thunk: => RIO[R, A]): RIO[R, A] =
    RIO.effectSuspend(thunk)

  override final def delay[A](thunk: => A): RIO[R, A] =
    RIO.effect(thunk)

  override final def bracket[A, B](acquire: RIO[R, A])(use: A => RIO[R, B])(release: A => RIO[R, Unit]): RIO[R, B] =
    RIO.bracket[R, A, B](acquire, release(_).orDie, use)

  override final def bracketCase[A, B](acquire: RIO[R, A])(use: A => RIO[R, B])(
    release: (A, ExitCase[Throwable]) => RIO[R, Unit]
  ): RIO[R, B] =
    RIO.bracketExit[R, A, B](acquire, (a, exit) => release(a, exitToExitCase(exit)).orDie, use)

  override final def uncancelable[A](fa: RIO[R, A]): RIO[R, A] =
    fa.uninterruptible

  override def guarantee[A](fa: RIO[R, A])(finalizer: RIO[R, Unit]): RIO[R, A] =
    fa.ensuring(finalizer.orDie)
}

private class CatsMonad[R, E] extends Monad[ZIO[R, E, *]] with StackSafeMonad[ZIO[R, E, *]] {
  override final def pure[A](a: A): ZIO[R, E, A]                                         = ZIO.succeed(a)
  override final def map[A, B](fa: ZIO[R, E, A])(f: A => B): ZIO[R, E, B]                = fa.map(f)
  override final def flatMap[A, B](fa: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] = fa.flatMap(f)

  override final def widen[A, B >: A](fa: ZIO[R, E, A]): ZIO[R, E, B]                                = fa
  override final def map2[A, B, Z](fa: ZIO[R, E, A], fb: ZIO[R, E, B])(f: (A, B) => Z): ZIO[R, E, Z] = fa.zipWith(fb)(f)
  override final def as[A, B](fa: ZIO[R, E, A], b: B): ZIO[R, E, B]                                  = fa.as(b)
  override final def whenA[A](cond: Boolean)(f: => ZIO[R, E, A]): ZIO[R, E, Unit]                    = ZIO.effectSuspendTotal(f).when(cond)
  override final def unit: ZIO[R, E, Unit]                                                           = ZIO.unit
}

private class CatsMonadError[R, E] extends CatsMonad[R, E] with MonadError[ZIO[R, E, *], E] {
  override final def handleErrorWith[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] = fa.catchAll(f)
  override final def raiseError[A](e: E): ZIO[R, E, A]                                        = ZIO.fail(e)

  override def attempt[A](fa: ZIO[R, E, A]): ZIO[R, E, Either[E, A]] = fa.either
}

/** lossy, throws away errors using the "first success" interpretation of SemigroupK */
private class CatsSemigroupKLossy[R, E] extends SemigroupK[ZIO[R, E, *]] {
  override final def combineK[A](a: ZIO[R, E, A], b: ZIO[R, E, A]): ZIO[R, E, A] = a.orElse(b)
}

private class CatsSemigroupK[R, E: Semigroup] extends SemigroupK[ZIO[R, E, *]] {
  override final def combineK[A](a: ZIO[R, E, A], b: ZIO[R, E, A]): ZIO[R, E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        ZIO.fail(Semigroup[E].combine(e1, e2))
      }
    }
}

private class CatsMonoidK[R, E: Monoid] extends CatsSemigroupK[R, E] with MonoidK[ZIO[R, E, *]] {
  override final def empty[A]: ZIO[R, E, A] = ZIO.fail(Monoid[E].empty)
}

private class CatsBifunctor[R] extends Bifunctor[ZIO[R, *, *]] {
  override final def bimap[A, B, C, D](fab: ZIO[R, A, B])(f: A => C, g: B => D): ZIO[R, C, D] =
    fab.bimap(f, g)
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
    Par(ZIO.succeed(x))

  final override def map2[A, B, Z](fa: ParIO[R, E, A], fb: ParIO[R, E, B])(f: (A, B) => Z): ParIO[R, E, Z] =
    Par(Par.unwrap(fa).zipWithPar(Par.unwrap(fb))(f))

  final override def ap[A, B](ff: ParIO[R, E, A => B])(fa: ParIO[R, E, A]): ParIO[R, E, B] =
    Par(Par.unwrap(ff).zipWithPar(Par.unwrap(fa))(_(_)))

  final override def product[A, B](fa: ParIO[R, E, A], fb: ParIO[R, E, B]): ParIO[R, E, (A, B)] =
    Par(Par.unwrap(fa).zipPar(Par.unwrap(fb)))

  final override def map[A, B](fa: ParIO[R, E, A])(f: A => B): ParIO[R, E, B] =
    Par(Par.unwrap(fa).map(f))

  final override def unit: ParIO[R, E, Unit] =
    Par(ZIO.unit)
}

private class CatsArrow[E] extends ArrowChoice[ZIO[*, E, *]] {
  final override def lift[A, B](f: A => B): ZIO[A, E, B]                              = ZIO.fromFunction(f)
  final override def compose[A, B, C](f: ZIO[B, E, C], g: ZIO[A, E, B]): ZIO[A, E, C] = g >>= f.provide
  final override def id[A]: ZIO[A, E, A]                                              = ZIO.environment
  final override def dimap[A, B, C, D](fab: ZIO[A, E, B])(f: C => A)(g: B => D): ZIO[C, E, D] =
    fab.provideSome(f).map(g)

  def choose[A, B, C, D](f: ZIO[A, E, C])(g: ZIO[B, E, D]): ZIO[Either[A, B], E, Either[C, D]] =
    ZIO.accessM[Either[A, B]] {
      case Left(a)  => f.provide(a).map(Left(_))
      case Right(b) => g.provide(b).map(Right(_))
    }

  final override def first[A, B, C](fa: ZIO[A, E, B]): ZIO[(A, C), E, (B, C)] =
    ZIO.accessM { case (a, c) => fa.provide(a).map((_, c)) }
  final override def second[A, B, C](fa: ZIO[A, E, B]): ZIO[(C, A), E, (C, B)] =
    ZIO.accessM { case (c, a) => fa.provide(a).map((c, _)) }
  final override def split[A, B, C, D](f: ZIO[A, E, B], g: ZIO[C, E, D]): ZIO[(A, C), E, (B, D)] =
    ZIO.accessM { case (a, c) => f.provide(a).zip(g.provide(c)) }
  final override def merge[A, B, C](f: ZIO[A, E, B], g: ZIO[A, E, C]): ZIO[A, E, (B, C)] = f.zip(g)

  final override def lmap[A, B, C](fab: ZIO[A, E, B])(f: C => A): ZIO[C, E, B] = fab.provideSome(f)
  final override def rmap[A, B, C](fab: ZIO[A, E, B])(f: B => C): ZIO[A, E, C] = fab.map(f)

  override def choice[A, B, C](f: ZIO[A, E, C], g: ZIO[B, E, C]): ZIO[Either[A, B], E, C] =
    ZIO.accessM[Either[A, B]] {
      case Left(a)  => f.provide(a)
      case Right(b) => g.provide(b)
    }
}
