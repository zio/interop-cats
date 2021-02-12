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

package zio
package interop

import cats.arrow.{ ArrowChoice, FunctionK }
import cats.effect.{ Async, Effect, ExitCase, LiftIO, Resource, Sync, IO => CIO }
import cats.{ ~>, Bifunctor, Monad, MonadError, Monoid, Semigroup, SemigroupK }
import zio.ZManaged.ReleaseMap

trait CatsZManagedSyntax {
  import scala.language.implicitConversions

  implicit final def catsIOResourceSyntax[F[_], A](resource: Resource[F, A]): CatsIOResourceSyntax[F, A] =
    new CatsIOResourceSyntax(resource)

  implicit final def zioResourceSyntax[R, E <: Throwable, A](r: Resource[ZIO[R, E, *], A]): ZIOResourceSyntax[R, E, A] =
    new ZIOResourceSyntax(r)

  implicit final def zManagedSyntax[R, E, A](managed: ZManaged[R, E, A]): ZManagedSyntax[R, E, A] =
    new ZManagedSyntax(managed)

}

final class CatsIOResourceSyntax[F[_], A](private val resource: Resource[F, A]) extends AnyVal {

  /**
   * Convert a cats Resource into a ZManaged.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toManaged[R](implicit L: LiftIO[ZIO[R, Throwable, *]], F: Effect[F]): ZManaged[R, Throwable, A] = {
    import catz.core._

    new ZIOResourceSyntax(
      resource.mapK(
        new ~>[F, ZIO[R, Throwable, *]] {
          def apply[A](fa: F[A]): ZIO[R, Throwable, A] =
            L liftIO F.toIO(fa)
        }
      )
    ).toManagedZIO
  }
}

final class ZManagedSyntax[R, E, A](private val managed: ZManaged[R, E, A]) extends AnyVal {

  def toResourceZIO: Resource[ZIO[R, E, *], A] =
    Resource
      .applyCase[ZIO[R, E, *], ReleaseMap](
        ZManaged.ReleaseMap.make.map(
          releaseMap =>
            (
              releaseMap,
              (exitCase: ExitCase[Throwable]) =>
                releaseMap.releaseAll(exitCaseToExit(exitCase), ExecutionStrategy.Sequential).unit
            )
        )
      )
      .flatMap(
        releaseMap =>
          Resource.suspend(
            managed.zio.provideSome[R]((_, releaseMap)).map { a =>
              Resource.applyCase(ZIO.succeedNow((a._2, (_: ExitCase[Throwable]) => ZIO.unit)))
            }
          )
      )

  def toResource[F[_]](implicit F: Async[F], ev: Effect[ZIO[R, E, *]]): Resource[F, A] =
    toResourceZIO.mapK(new FunctionK[ZIO[R, E, *], F] {
      def apply[A](fa: ZIO[R, E, A]): F[A] =
        F liftIO ev.toIO(fa)
    })

}

trait CatsEffectZManagedInstances {

  implicit def liftIOZManagedInstances[R](
    implicit ev: LiftIO[ZIO[R, Throwable, *]]
  ): LiftIO[ZManaged[R, Throwable, *]] =
    new LiftIO[ZManaged[R, Throwable, *]] {
      override def liftIO[A](ioa: CIO[A]): ZManaged[R, Throwable, A] =
        ZManaged.fromEffect(ev.liftIO(ioa))
    }

  implicit def syncZManagedInstances[R]: Sync[ZManaged[R, Throwable, *]] =
    new CatsZManagedSync[R]

}

trait CatsZManagedInstances extends CatsZManagedInstances1 {

  implicit def monadErrorZManagedInstances[R, E]: MonadError[ZManaged[R, E, *], E] =
    new CatsZManagedMonadError

  implicit def monoidZManagedInstances[R, E, A](implicit ev: Monoid[A]): Monoid[ZManaged[R, E, A]] =
    new Monoid[ZManaged[R, E, A]] {
      override def empty: ZManaged[R, E, A] = ZManaged.succeedNow(ev.empty)

      override def combine(x: ZManaged[R, E, A], y: ZManaged[R, E, A]): ZManaged[R, E, A] = x.zipWith(y)(ev.combine)
    }

  implicit def arrowChoiceRManagedInstances[E]: ArrowChoice[RManaged] =
    CatsZManagedArrowChoice.asInstanceOf[ArrowChoice[RManaged]]
}

sealed trait CatsZManagedInstances1 extends CatsZManagedInstances2 {

  implicit def monadZManagedInstances[R, E]: Monad[ZManaged[R, E, *]] = new CatsZManagedMonad

  implicit def semigroupZManagedInstances[R, E, A](implicit ev: Semigroup[A]): Semigroup[ZManaged[R, E, A]] =
    (x: ZManaged[R, E, A], y: ZManaged[R, E, A]) => x.zipWith(y)(ev.combine)

  implicit def semigroupKZManagedInstances[R, E]: SemigroupK[ZManaged[R, E, *]] = new CatsZManagedSemigroupK

  implicit def bifunctorZManagedInstances[R]: Bifunctor[ZManaged[R, *, *]] = new Bifunctor[ZManaged[R, *, *]] {
    override def bimap[A, B, C, D](fab: ZManaged[R, A, B])(f: A => C, g: B => D): ZManaged[R, C, D] =
      fab.bimap(f, g)
  }

  implicit def arrowChoiceURManagedInstances[E]: ArrowChoice[URManaged] =
    CatsZManagedArrowChoice.asInstanceOf[ArrowChoice[URManaged]]
}

sealed trait CatsZManagedInstances2 {
  implicit def arrowChoiceZManagedInstances[E]: ArrowChoice[ZManaged[*, E, *]] =
    CatsZManagedArrowChoice.asInstanceOf[ArrowChoice[ZManaged[*, E, *]]]
}

private class CatsZManagedMonad[R, E] extends Monad[ZManaged[R, E, *]] {
  override def pure[A](x: A): ZManaged[R, E, A] = ZManaged.succeedNow(x)

  override def flatMap[A, B](fa: ZManaged[R, E, A])(f: A => ZManaged[R, E, B]): ZManaged[R, E, B] = fa.flatMap(f)

  override def tailRecM[A, B](a: A)(f: A => ZManaged[R, E, Either[A, B]]): ZManaged[R, E, B] =
    ZManaged.suspend(f(a)).flatMap {
      case Left(nextA) => tailRecM(nextA)(f)
      case Right(b)    => ZManaged.succeedNow(b)
    }
}

private class CatsZManagedMonadError[R, E] extends CatsZManagedMonad[R, E] with MonadError[ZManaged[R, E, *], E] {
  override def raiseError[A](e: E): ZManaged[R, E, A] = ZManaged.fromEffect(ZIO.fail(e))

  override def handleErrorWith[A](fa: ZManaged[R, E, A])(f: E => ZManaged[R, E, A]): ZManaged[R, E, A] =
    fa.catchAll(f)
}

/**
 * lossy, throws away errors using the "first success" interpretation of SemigroupK
 */
private class CatsZManagedSemigroupK[R, E] extends SemigroupK[ZManaged[R, E, *]] {
  override def combineK[A](x: ZManaged[R, E, A], y: ZManaged[R, E, A]): ZManaged[R, E, A] =
    x.orElse(y)
}

private class CatsZManagedSync[R] extends CatsZManagedMonadError[R, Throwable] with Sync[ZManaged[R, Throwable, *]] {

  override final def delay[A](thunk: => A): ZManaged[R, Throwable, A] = ZManaged.fromEffect(ZIO.effect(thunk))

  override final def suspend[A](thunk: => ZManaged[R, Throwable, A]): ZManaged[R, Throwable, A] =
    ZManaged.unwrap(ZIO.effect(thunk))

  override def bracketCase[A, B](
    acquire: ZManaged[R, Throwable, A]
  )(use: A => ZManaged[R, Throwable, B])(
    release: (A, cats.effect.ExitCase[Throwable]) => ZManaged[R, Throwable, Unit]
  ): ZManaged[R, Throwable, B] =
    ZManaged {
      ZIO.uninterruptibleMask { restore =>
        (for {
          a     <- acquire
          exitB <- ZManaged(restore(use(a).zio)).run
          _     <- release(a, exitToExitCase(exitB))
          b     <- ZManaged.done(exitB)
        } yield b).zio
      }
    }
}

private object CatsZManagedArrowChoice extends ArrowChoice[ZManaged[*, Any, *]] {
  final override def lift[A, B](f: A => B): ZManaged[A, Any, B] = ZManaged.fromFunction(f)
  final override def compose[A, B, C](f: ZManaged[B, Any, C], g: ZManaged[A, Any, B]): ZManaged[A, Any, C] =
    f compose g

  final override def id[A]: ZManaged[A, Any, A] = ZManaged.identity
  final override def dimap[A, B, C, D](fab: ZManaged[A, Any, B])(f: C => A)(g: B => D): ZManaged[C, Any, D] =
    fab.provideSome(f).map(g)

  final override def choose[A, B, C, D](f: ZManaged[A, Any, C])(
    g: ZManaged[B, Any, D]
  ): ZManaged[Either[A, B], Any, Either[C, D]] = f +++ g

  final override def first[A, B, C](fa: ZManaged[A, Any, B]): ZManaged[(A, C), Any, (B, C)]  = fa *** ZManaged.identity
  final override def second[A, B, C](fa: ZManaged[A, Any, B]): ZManaged[(C, A), Any, (C, B)] = ZManaged.identity *** fa
  final override def split[A, B, C, D](f: ZManaged[A, Any, B], g: ZManaged[C, Any, D]): ZManaged[(A, C), Any, (B, D)] =
    f *** g

  final override def merge[A, B, C](f: ZManaged[A, Any, B], g: ZManaged[A, Any, C]): ZManaged[A, Any, (B, C)] = f.zip(g)
  final override def lmap[A, B, C](fab: ZManaged[A, Any, B])(f: C => A): ZManaged[C, Any, B]                  = fab.provideSome(f)
  final override def rmap[A, B, C](fab: ZManaged[A, Any, B])(f: B => C): ZManaged[A, Any, C]                  = fab.map(f)
  final override def choice[A, B, C](f: ZManaged[A, Any, C], g: ZManaged[B, Any, C]): ZManaged[Either[A, B], Any, C] =
    f ||| g
}
