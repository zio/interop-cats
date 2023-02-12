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

import cats.*
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.kernel.{ CommutativeMonoid, CommutativeSemigroup }
import zio.Trace
import zio.interop.catz.concurrentInstance
import zio.internal.stacktracer.InteropTracer
import zio.internal.stacktracer.{ Tracer => CoreTracer }
import zio.managed.*

trait CatsZManagedSyntax {
  import scala.language.implicitConversions

  implicit final def zioResourceSyntax[R, E <: Throwable, A](
    resource: Resource[ZIO[R, E, _], A]
  ): ZIOResourceSyntax[R, E, A] =
    new ZIOResourceSyntax(resource)

  implicit final def catsIOResourceSyntax[F[_], A](resource: Resource[F, A]): CatsIOResourceSyntax[F, A] =
    new CatsIOResourceSyntax(resource)

  implicit final def zManagedSyntax[R, E, A](managed: ZManaged[R, E, A]): ZManagedSyntax[R, E, A] =
    new ZManagedSyntax(managed)

  implicit final def scopedSyntax(resource: Resource.type): ScopedSyntax =
    new ScopedSyntax(resource)
}

final class CatsIOResourceSyntax[F[_], A](private val resource: Resource[F, A]) extends AnyVal {
  def toManaged(implicit F: MonadCancel[F, ?], D: Dispatcher[F], trace: Trace): TaskManaged[A] =
    new ZIOResourceSyntax(resource.mapK(new (F ~> Task) {
      override def apply[B](fb: F[B]) = fromEffect(fb)
    })).toManagedZIO
}

final class ZIOResourceSyntax[R, E <: Throwable, A](private val resource: Resource[ZIO[R, E, _], A]) extends AnyVal {

  /**
   * Convert a cats Resource into a ZManaged.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toManagedZIO(implicit trace: Trace): ZManaged[R, E, A] =
    ZManaged.scoped[R](toScopedZIO)

  /**
   * Convert a cats Resource into a scoped ZIO.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toScopedZIO(implicit trace: Trace): ZIO[R with Scope, E, A] = {
    type F[T] = ZIO[R, E, T]

    def go[B](resource: Resource[F, B]): ZIO[R with Scope, E, B] =
      resource match {
        case allocate: Resource.Allocate[F, b] =>
          ZIO.acquireReleaseExit {
            ZIO.uninterruptibleMask { restore =>
              allocate.resource(toPoll(restore))
            }
          } { case ((_, release), exit) => toExitCaseThisFiber(exit).flatMap(t => release(t)).orDie }.map(_._1)

        case bind: Resource.Bind[F, a, B] =>
          go(bind.source).flatMap(a => go(bind.fs(a)))

        case eval: Resource.Eval[F, b] =>
          eval.fa

        case pure: Resource.Pure[F, B] =>
          ZIO.succeed(pure.a)
      }

    go(resource)
  }
}

final class ZManagedSyntax[R, E, A](private val managed: ZManaged[R, E, A]) extends AnyVal {
  import zio.interop.catz.scopedSyntax

  def toResourceZIO(implicit trace: Trace): Resource[ZIO[R, E, _], A] =
    Resource.scopedZIO[R, E, A](managed.scoped)

  def toResource[F[_]: Async](implicit R: Runtime[R], ev: E <:< Throwable, trace: Trace): Resource[F, A] =
    Resource.scoped[F, R, A](managed.scoped.mapError(ev))
}

final class ScopedSyntax(private val self: Resource.type) extends AnyVal {
  def scopedZIO[R, E, A](zio: ZIO[Scope with R, E, A])(implicit trace: Trace): Resource[ZIO[R, E, _], A] = {
    type F[T] = ZIO[R, E, T]

    Resource
      .applyCase[F, Scope.Closeable](
        Scope.make.map { scope =>
          (scope, exitCase => scope.close(toExit(exitCase)))
        }
      )
      .flatMap { scope =>
        Resource.suspend(
          scope.extend[R] {
            zio.map { case a =>
              Resource.applyCase[F, A](ZIO.succeed((a, _ => ZIO.unit)))
            }
          }
        )
      }
  }

  def scoped[F[_]: Async, R, A](
    zio: ZIO[Scope with R, Throwable, A]
  )(implicit runtime: Runtime[R], trace: Trace): Resource[F, A] =
    scopedZIO[R, Throwable, A](zio).mapK(new (ZIO[R, Throwable, _] ~> F) {
      override def apply[B](zio: ZIO[R, Throwable, B]) = toEffect(zio)
    })
}

trait CatsEffectZManagedInstances {
  implicit def liftIOZManagedInstances[R](implicit ev: LiftIO[RIO[R, _]]): LiftIO[RManaged[R, _]] =
    new ZManagedLiftIO
}

trait CatsZManagedInstances extends CatsZManagedInstances1 {
  type ParZManaged[R, E, A] = ParallelF[ZManaged[R, E, _], A]

  implicit final def monadErrorZManagedInstances[R, E]: MonadError[ZManaged[R, E, _], E] =
    monadErrorInstance0.asInstanceOf[MonadError[ZManaged[R, E, _], E]]

  implicit def monoidZManagedInstances[R, E, A: Monoid]: Monoid[ZManaged[R, E, A]] =
    new ZManagedMonoid

  implicit def parMonoidZManagedInstances[R, E, A: CommutativeMonoid]: CommutativeMonoid[ParZManaged[R, E, A]] =
    new ZManagedParMonoid

  implicit final def monoidKZManagedInstances[R, E: Monoid]: MonoidK[ZManaged[R, E, _]] =
    new ZManagedMonoidK

  implicit final def parallelZManagedInstances[R, E]: Parallel.Aux[ZManaged[R, E, _], ParallelF[ZManaged[R, E, _], _]] =
    parallelInstance0.asInstanceOf[Parallel.Aux[ZManaged[R, E, _], ParallelF[ZManaged[R, E, _], _]]]

  private[this] val monadErrorInstance0: MonadError[TaskManaged, Throwable] =
    new ZManagedMonadError[Any, Throwable]

  private[this] lazy val parallelInstance0: Parallel.Aux[TaskManaged, ParallelF[TaskManaged, _]] =
    new ZManagedParallel
}

sealed trait CatsZManagedInstances1 {

  implicit def semigroupZManagedInstances[R, E, A: Semigroup]: Semigroup[ZManaged[R, E, A]] =
    new ZManagedSemigroup

  implicit def parSemigroupZManagedInstances[R, E, A: CommutativeSemigroup]
    : CommutativeSemigroup[ParallelF[ZManaged[R, E, _], A]] =
    new ZManagedParSemigroup

  implicit final def semigroupKZManagedInstances[R, E]: SemigroupK[ZManaged[R, E, _]] =
    semigroupKInstance0.asInstanceOf[SemigroupK[ZManaged[R, E, _]]]

  implicit final def bifunctorZManagedInstances[R]: Bifunctor[ZManaged[R, _, _]] =
    bifunctorInstance0.asInstanceOf[Bifunctor[ZManaged[R, _, _]]]

  private[this] val semigroupKInstance0: SemigroupK[TaskManaged] =
    new ZManagedSemigroupK[Any, Throwable]

  private[this] val bifunctorInstance0: Bifunctor[Managed] =
    new ZManagedBifunctor[Any]
}

private class ZManagedMonadError[R, E] extends MonadError[ZManaged[R, E, _], E] {
  type F[A] = ZManaged[R, E, A]

  override final def pure[A](a: A): F[A] =
    ZManaged.succeed(a)

  override final def map[A, B](fa: F[A])(f: A => B): F[B] =
    fa.map(f)(InteropTracer.newTrace(f))

  override final def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] =
    fa.flatMap(f)(InteropTracer.newTrace(f))

  override final def flatTap[A, B](fa: F[A])(f: A => F[B]): F[A] =
    fa.tap(f)(InteropTracer.newTrace(f))

  override final def widen[A, B >: A](fa: F[A]): F[B] =
    fa

  override final def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    fa.zipWith(fb)(f)(InteropTracer.newTrace(f))

  override final def as[A, B](fa: F[A], b: B): F[B] =
    fa.as(b)(CoreTracer.newTrace)

  override final def whenA[A](cond: Boolean)(f: => F[A]): F[Unit] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ZManaged.when(cond)(f).unit
  }

  override final def unit: F[Unit] =
    ZManaged.unit

  override final def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] =
    fa.catchAll(f)(implicitly[CanFail[E]], InteropTracer.newTrace(f))

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
    fa.catchSome(pf)(implicitly[CanFail[E]], CoreTracer.newTrace)

  override final def raiseError[A](e: E): F[A] =
    ZManaged.fail(e)(CoreTracer.newTrace)

  override final def attempt[A](fa: F[A]): F[Either[E, A]] =
    fa.either(implicitly[CanFail[E]], CoreTracer.newTrace)

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[E, E]): F[A] =
    fa.mapError(pf.orElse { case error => error })(implicitly[CanFail[E]], CoreTracer.newTrace)

  override final def tailRecM[A, B](a: A)(f: A => ZManaged[R, E, Either[A, B]]): ZManaged[R, E, B] = {
    implicit def trace: Trace = InteropTracer.newTrace(f)

    ZManaged.suspend(f(a)).flatMap {
      case Left(a)  => tailRecM(a)(f)
      case Right(b) => ZManaged.succeed(b)
    }
  }
}

private class ZManagedSemigroup[R, E, A](implicit semigroup: Semigroup[A]) extends Semigroup[ZManaged[R, E, A]] {
  type T = ZManaged[R, E, A]

  override final def combine(x: T, y: T): T =
    x.zipWith(y)(semigroup.combine)(CoreTracer.newTrace)
}

private class ZManagedMonoid[R, E, A](implicit monoid: Monoid[A])
    extends ZManagedSemigroup[R, E, A]
    with Monoid[ZManaged[R, E, A]] {

  override final val empty: T =
    ZManaged.succeed(monoid.empty)
}

private class ZManagedSemigroupK[R, E] extends SemigroupK[ZManaged[R, E, _]] {
  type F[A] = ZManaged[R, E, A]

  override final def combineK[A](x: F[A], y: F[A]): F[A] =
    x.orElse(y)(implicitly[CanFail[E]], CoreTracer.newTrace)
}

private class ZManagedMonoidK[R, E](implicit monoid: Monoid[E]) extends MonoidK[ZManaged[R, E, _]] {
  type F[A] = ZManaged[R, E, A]

  override final def empty[A]: F[A] =
    ZManaged.fail(monoid.empty)(CoreTracer.newTrace)

  override final def combineK[A](a: F[A], b: F[A]): F[A] = {
    implicit val tracer: Trace = CoreTracer.newTrace

    a.catchAll(e1 => b.catchAll(e2 => ZManaged.fail(monoid.combine(e1, e2))))
  }
}

private class ZManagedParSemigroup[R, E, A](implicit semigroup: CommutativeSemigroup[A])
    extends CommutativeSemigroup[ParallelF[ZManaged[R, E, _], A]] {

  type T = ParallelF[ZManaged[R, E, _], A]

  override final def combine(x: T, y: T): T = {
    implicit val tracer: Trace = CoreTracer.newTrace

    ParallelF(ParallelF.value(x).zipWithPar(ParallelF.value(y))(semigroup.combine))
  }
}

private class ZManagedParMonoid[R, E, A](implicit monoid: CommutativeMonoid[A])
    extends ZManagedParSemigroup[R, E, A]
    with CommutativeMonoid[ParallelF[ZManaged[R, E, _], A]] {

  override final val empty: T =
    ParallelF[ZManaged[R, E, _], A](ZManaged.succeed(monoid.empty))
}

private class ZManagedBifunctor[R] extends Bifunctor[ZManaged[R, _, _]] {
  type F[A, B] = ZManaged[R, A, B]

  override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] =
    fab.mapBoth(f, g)(implicitly[CanFail[A]], InteropTracer.newTrace(f))
}

private class ZManagedParallel[R, E](final override implicit val monad: Monad[ZManaged[R, E, _]])
    extends Parallel[ZManaged[R, E, _]] {

  type G[A] = ZManaged[R, E, A]
  type F[A] = ParallelF[G, A]

  final override val applicative: Applicative[F] =
    new ZManagedParApplicative[R, E]

  final override val sequential: F ~> G = new (F ~> G) {
    def apply[A](fa: F[A]): G[A] = ParallelF.value(fa)
  }

  final override val parallel: G ~> F = new (G ~> F) {
    def apply[A](fa: G[A]): F[A] = ParallelF(fa)
  }
}

private class ZManagedParApplicative[R, E] extends CommutativeApplicative[ParallelF[ZManaged[R, E, _], _]] {
  type G[A] = ZManaged[R, E, A]
  type F[A] = ParallelF[G, A]

  final override def pure[A](x: A): F[A] =
    ParallelF[G, A](ZManaged.succeed(x))

  final override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = {
    implicit val tracer: Trace = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).zipWithPar(ParallelF.value(fb))(f))
  }

  final override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    map2(ff, fa)(_ apply _)

  final override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = {
    implicit val tracer: Trace = CoreTracer.newTrace

    ParallelF(ParallelF.value(fa).zipPar(ParallelF.value(fb)))
  }

  final override def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit val tracer: Trace = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).map(f))
  }

  final override val unit: F[Unit] =
    ParallelF[G, Unit](ZManaged.unit)
}

private class ZManagedLiftIO[R](implicit ev: LiftIO[RIO[R, _]]) extends LiftIO[RManaged[R, _]] {
  override final def liftIO[A](ioa: effect.IO[A]): RManaged[R, A] =
    ZManaged.fromZIO(ev.liftIO(ioa))(CoreTracer.newTrace)
}
