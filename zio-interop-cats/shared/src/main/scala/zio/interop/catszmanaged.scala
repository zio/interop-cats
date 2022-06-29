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
import cats.arrow.ArrowChoice
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.kernel.{ CommutativeMonoid, CommutativeSemigroup }
import zio.ZManaged.ReleaseMap
import zio.interop.catz.*

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

}

final class CatsIOResourceSyntax[F[_], A](private val resource: Resource[F, A]) extends AnyVal {
  def toManaged(implicit F: MonadCancel[F, ?], D: Dispatcher[F]): TaskManaged[A] =
    new ZIOResourceSyntax(resource.mapK(new (F ~> Task) {
      override def apply[B](fb: F[B]) = fromEffect(fb)
    })).toManagedZIO
}

final class ZIOResourceSyntax[R, E <: Throwable, A](private val resource: Resource[ZIO[R, E, _], A]) extends AnyVal {

  /**
   * Convert a cats Resource into a ZManaged.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toManagedZIO: ZManaged[R, E, A] = {
    type F[T] = ZIO[R, E, T]

    def go[B](resource: Resource[F, B]): ZManaged[R, E, B] =
      resource match {
        case allocate: Resource.Allocate[F, b] =>
          ZManaged {
            ZIO.uninterruptibleMask { restore =>
              for {
                r               <- ZIO.environment[(R, ReleaseMap)]
                af              <- allocate.resource(toPoll(restore)).provide(r._1)
                (a, release)     = af
                releaseMapEntry <- r._2.add(toExitCaseThisFiber(_).flatMap(release).provide(r._1).orDie)
              } yield (releaseMapEntry, a)
            }
          }

        case bind: Resource.Bind[F, a, B] =>
          go(bind.source).flatMap(a => go(bind.fs(a)))

        case eval: Resource.Eval[F, b] =>
          ZManaged.fromEffect(eval.fa)

        case pure: Resource.Pure[F, B] =>
          ZManaged.succeedNow(pure.a)
      }

    go(resource)
  }
}

final class ZManagedSyntax[R, E, A](private val managed: ZManaged[R, E, A]) extends AnyVal {
  def toResourceZIO: Resource[ZIO[R, E, _], A] = {
    type F[T] = ZIO[R, E, T]

    Resource
      .applyCase[F, ReleaseMap](
        ZManaged.ReleaseMap.make.map { releaseMap =>
          (releaseMap, exitCase => releaseMap.releaseAll(toExit(exitCase), ExecutionStrategy.Sequential).unit)
        }
      )
      .flatMap { releaseMap =>
        Resource.suspend(
          managed.zio.provideSome[R]((_, releaseMap)).map { case (_, a) =>
            Resource.applyCase[F, A](ZIO.succeedNow((a, _ => ZIO.unit)))
          }
        )
      }
  }

  def toResource[F[_]: Async](implicit R: Runtime[R], ev: E <:< Throwable): Resource[F, A] =
    managed
      .mapError(ev)
      .toResourceZIO
      .mapK(new (ZIO[R, Throwable, _] ~> F) {
        override def apply[B](zio: ZIO[R, Throwable, B]): F[B] = toEffect[F, R, B](zio)
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

  implicit final def contravariantZManagedInstances[E, A]: Contravariant[ZManaged[_, E, A]] =
    contravariantInstance0.asInstanceOf[Contravariant[ZManaged[_, E, A]]]

  implicit def monoidZManagedInstances[R, E, A: Monoid]: Monoid[ZManaged[R, E, A]] =
    new ZManagedMonoid

  implicit def parMonoidZManagedInstances[R, E, A: CommutativeMonoid]: CommutativeMonoid[ParZManaged[R, E, A]] =
    new ZManagedParMonoid

  implicit final def monoidKZManagedInstances[R, E: Monoid]: MonoidK[ZManaged[R, E, _]] =
    new ZManagedMonoidK

  implicit final def arrowChoiceRManagedInstances: ArrowChoice[RManaged] =
    arrowChoiceZManagedInstance0

  implicit final def parallelZManagedInstances[R, E]: Parallel.Aux[ZManaged[R, E, _], ParallelF[ZManaged[R, E, _], _]] =
    parallelInstance0.asInstanceOf[Parallel.Aux[ZManaged[R, E, _], ParallelF[ZManaged[R, E, _], _]]]

  private[this] val monadErrorInstance0: MonadError[TaskManaged, Throwable] =
    new ZManagedMonadError[Any, Throwable]

  private[this] val contravariantInstance0: Contravariant[RManaged[_, Any]] =
    new ZManagedContravariant

  private[this] lazy val parallelInstance0: Parallel.Aux[TaskManaged, ParallelF[TaskManaged, _]] =
    new ZManagedParallel
}

sealed trait CatsZManagedInstances1 extends CatsZManagedInstances2 {

  implicit def semigroupZManagedInstances[R, E, A: Semigroup]: Semigroup[ZManaged[R, E, A]] =
    new ZManagedSemigroup

  implicit def parSemigroupZManagedInstances[R, E, A: CommutativeSemigroup]
    : CommutativeSemigroup[ParallelF[ZManaged[R, E, _], A]] =
    new ZManagedParSemigroup

  implicit final def semigroupKZManagedInstances[R, E]: SemigroupK[ZManaged[R, E, _]] =
    semigroupKInstance0.asInstanceOf[SemigroupK[ZManaged[R, E, _]]]

  implicit final def bifunctorZManagedInstances[R]: Bifunctor[ZManaged[R, _, _]] =
    bifunctorInstance0.asInstanceOf[Bifunctor[ZManaged[R, _, _]]]

  implicit final def arrowChoiceURManagedInstances: ArrowChoice[URManaged] =
    arrowChoiceZManagedInstance0.asInstanceOf[ArrowChoice[URManaged]]

  private[this] val semigroupKInstance0: SemigroupK[TaskManaged] =
    new ZManagedSemigroupK[Any, Throwable]

  private[this] val bifunctorInstance0: Bifunctor[Managed] =
    new ZManagedBifunctor[Any]
}

sealed trait CatsZManagedInstances2 {
  implicit final def arrowChoiceZManagedInstances[E]: ArrowChoice[ZManaged[_, E, _]] =
    arrowChoiceZManagedInstance0.asInstanceOf[ArrowChoice[ZManaged[_, E, _]]]

  protected[this] final val arrowChoiceZManagedInstance0: ArrowChoice[RManaged] =
    new ZManagedArrowChoice
}

private class ZManagedMonadError[R, E] extends MonadError[ZManaged[R, E, _], E] {
  type F[A] = ZManaged[R, E, A]

  override final def pure[A](a: A): F[A] =
    ZManaged.succeedNow(a)

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
    ZManaged.when(cond)(f)

  override final def unit: F[Unit] =
    ZManaged.unit

  override final def handleErrorWith[A](fa: F[A])(f: E => F[A]): F[A] =
    fa.catchAll(f)

  override final def recoverWith[A](fa: F[A])(pf: PartialFunction[E, F[A]]): F[A] =
    fa.catchSome(pf)

  override final def raiseError[A](e: E): F[A] =
    ZManaged.fail(e)

  override final def attempt[A](fa: F[A]): F[Either[E, A]] =
    fa.either

  override final def adaptError[A](fa: F[A])(pf: PartialFunction[E, E]): F[A] =
    fa.mapError(pf.orElse { case error => error })

  override final def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = {
    def loop(a: A): F[B] = f(a).flatMap {
      case Left(a)  => loop(a)
      case Right(b) => ZManaged.succeedNow(b)
    }

    ZManaged.suspend(loop(a))
  }
}

private class ZManagedSemigroup[R, E, A](implicit semigroup: Semigroup[A]) extends Semigroup[ZManaged[R, E, A]] {
  type T = ZManaged[R, E, A]

  override final def combine(x: T, y: T): T =
    x.zipWith(y)(semigroup.combine)
}

private class ZManagedMonoid[R, E, A](implicit monoid: Monoid[A])
    extends ZManagedSemigroup[R, E, A]
    with Monoid[ZManaged[R, E, A]] {

  override final val empty: T =
    ZManaged.succeedNow(monoid.empty)
}

private class ZManagedSemigroupK[R, E] extends SemigroupK[ZManaged[R, E, _]] {
  type F[A] = ZManaged[R, E, A]

  override final def combineK[A](x: F[A], y: F[A]): F[A] =
    x orElse y
}

private class ZManagedMonoidK[R, E](implicit monoid: Monoid[E]) extends MonoidK[ZManaged[R, E, _]] {
  type F[A] = ZManaged[R, E, A]

  override final def empty[A]: F[A] =
    ZManaged.fail(monoid.empty)

  override final def combineK[A](a: F[A], b: F[A]): F[A] =
    a.catchAll(e1 => b.catchAll(e2 => ZManaged.fail(monoid.combine(e1, e2))))
}

private class ZManagedParSemigroup[R, E, A](implicit semigroup: CommutativeSemigroup[A])
    extends CommutativeSemigroup[ParallelF[ZManaged[R, E, _], A]] {

  type T = ParallelF[ZManaged[R, E, _], A]

  override final def combine(x: T, y: T): T =
    ParallelF(ParallelF.value(x).zipWithPar(ParallelF.value(y))(semigroup.combine))
}

private class ZManagedParMonoid[R, E, A](implicit monoid: CommutativeMonoid[A])
    extends ZManagedParSemigroup[R, E, A]
    with CommutativeMonoid[ParallelF[ZManaged[R, E, _], A]] {

  override final val empty: T =
    ParallelF[ZManaged[R, E, _], A](ZManaged.succeedNow(monoid.empty))
}

private class ZManagedBifunctor[R] extends Bifunctor[ZManaged[R, _, _]] {
  type F[A, B] = ZManaged[R, A, B]

  override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] =
    fab.mapBoth(f, g)
}

private class ZManagedContravariant[E, T] extends Contravariant[ZManaged[_, E, T]] {
  type F[A] = ZManaged[A, E, T]

  override final def contramap[A, B](fa: F[A])(f: B => A): F[B] =
    ZManaged.accessManaged[B](b => fa.provide(f(b)))
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
    ParallelF[G, A](ZManaged.succeedNow(x))

  final override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    ParallelF(ParallelF.value(fa).zipWithPar(ParallelF.value(fb))(f))

  final override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    map2(ff, fa)(_ apply _)

  final override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    ParallelF(ParallelF.value(fa).zipPar(ParallelF.value(fb)))

  final override def map[A, B](fa: F[A])(f: A => B): F[B] =
    ParallelF(ParallelF.value(fa).map(f))

  final override val unit: F[Unit] =
    ParallelF[G, Unit](ZManaged.unit)
}

private class ZManagedArrowChoice[E] extends ArrowChoice[ZManaged[_, E, _]] {
  type F[A, B] = ZManaged[A, E, B]

  final override def lift[A, B](f: A => B): F[A, B] =
    ZManaged.fromFunction(f)

  final override def compose[A, B, C](f: F[B, C], g: F[A, B]): F[A, C] =
    f compose g

  final override def id[A]: F[A, A] =
    ZManaged.identity[A]

  final override def dimap[A, B, C, D](fab: F[A, B])(f: C => A)(g: B => D): F[C, D] =
    fab.provideSome(f).map(g)

  final override def choose[A, B, C, D](f: F[A, C])(g: F[B, D]): F[Either[A, B], Either[C, D]] =
    f +++ g

  final override def first[A, B, C](fa: F[A, B]): F[(A, C), (B, C)] =
    fa *** ZManaged.identity[C]

  final override def second[A, B, C](fa: F[A, B]): F[(C, A), (C, B)] =
    ZManaged.identity[C] *** fa

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

private class ZManagedLiftIO[R](implicit ev: LiftIO[RIO[R, _]]) extends LiftIO[RManaged[R, _]] {
  override final def liftIO[A](ioa: effect.IO[A]): RManaged[R, A] =
    ZManaged.fromEffect(ev.liftIO(ioa))
}
