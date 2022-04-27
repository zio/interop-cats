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

package zio.stream.interop

import cats._
import zio._
import zio.stream._
import zio.internal.stacktracer.{ Tracer => CoreTracer }
import zio.internal.stacktracer.InteropTracer

object catz extends CatsInstances {
  object core extends CatsInstances
}

sealed abstract class CatsInstances extends CatsInstances1 {
  implicit def zstreamAlternativeInstance[R, E]: Alternative[ZStream[R, E, *]] =
    new CatsAlternative[R, E]
}

sealed abstract class CatsInstances1 extends CatsInstances2 {
  implicit def zstreamMonoidKInstance[R, E]: MonoidK[ZStream[R, E, *]] =
    new CatsMonoidK[R, E]

  implicit def zstreamBifunctorInstance[R]: Bifunctor[ZStream[R, *, *]] =
    new CatsBifunctor[R] {}
}

sealed abstract class CatsInstances2 extends CatsInstances3 {
  implicit def zstreamParallelInstance[R, E]: Parallel.Aux[ZStream[R, E, *], ParStream[R, E, *]] =
    new CatsParallel[R, E](zstreamMonadErrorInstance)

  implicit def zstreamSemigroupKInstanceBincompat0[R, E]: SemigroupK[ZStream[R, E, *]] = new CatsSemigroupK[R, E]

  private[zio] implicit def zstreamSemigroupKInstance[R, E](
    implicit @deprecated("bincompat", "2.4.1.0") evidence$1: Semigroup[E]
  ): SemigroupK[ZStream[R, E, *]] =
    new CatsSemigroupK[R, E]
}

sealed abstract class CatsInstances3 {
  implicit def zstreamMonadErrorInstance[R, E]: MonadError[ZStream[R, E, *], E] =
    new CatsMonadError[R, E]
}

private class CatsAlternative[R, E]
    extends CatsMonoidK[R, E]
    with CatsApplicative[R, E]
    with Alternative[ZStream[R, E, *]]

private class CatsMonadError[R, E] extends CatsMonad[R, E] with MonadError[ZStream[R, E, *], E] {
  override final def handleErrorWith[A](fa: ZStream[R, E, A])(f: E => ZStream[R, E, A]): ZStream[R, E, A] =
    fa.catchAll(f)(implicitly[CanFail[E]], InteropTracer.newTrace(f))
  override final def raiseError[A](e: E): ZStream[R, E, A] = ZStream.fail(e)(CoreTracer.newTrace)

  override def attempt[A](fa: ZStream[R, E, A]): ZStream[R, E, Either[E, A]] =
    fa.either(implicitly[CanFail[E]], CoreTracer.newTrace)
}

private trait CatsMonad[R, E] extends Monad[ZStream[R, E, *]] with StackSafeMonad[ZStream[R, E, *]] {
  override final def flatMap[A, B](fa: ZStream[R, E, A])(f: A => ZStream[R, E, B]): ZStream[R, E, B] =
    fa.flatMap(f)(InteropTracer.newTrace(f))
  override final def pure[A](a: A): ZStream[R, E, A]                              = ZStream.succeed(a)(CoreTracer.newTrace)
  override final def map[A, B](fa: ZStream[R, E, A])(f: A => B): ZStream[R, E, B] = fa.map(f)(InteropTracer.newTrace(f))

  override final def widen[A, B >: A](fa: ZStream[R, E, A]): ZStream[R, E, B] = fa
  override final def map2[A, B, Z](fa: ZStream[R, E, A], fb: ZStream[R, E, B])(f: (A, B) => Z): ZStream[R, E, Z] =
    fa.crossWith(fb)(f)(InteropTracer.newTrace(f))
  override final def as[A, B](fa: ZStream[R, E, A], b: B): ZStream[R, E, B] = fa.as(b)(CoreTracer.newTrace)
}

private trait CatsApplicative[R, E] extends Applicative[ZStream[R, E, *]] {
  override final def pure[A](a: A): ZStream[R, E, A]                              = ZStream.succeed(a)(CoreTracer.newTrace)
  override final def map[A, B](fa: ZStream[R, E, A])(f: A => B): ZStream[R, E, B] = fa.map(f)(InteropTracer.newTrace(f))
  override final def ap[A, B](ff: ZStream[R, E, A => B])(fa: ZStream[R, E, A]): ZStream[R, E, B] =
    ff.crossWith(fa)(_(_))(CoreTracer.newTrace)

  override final def unit: ZStream[R, E, Unit] = ZStream.unit
  override final def whenA[A](cond: Boolean)(f: => ZStream[R, E, A]): ZStream[R, E, Unit] =
    if (cond) {
      val byName: () => ZStream[R, E, A] = () => f
      f.as(())(InteropTracer.newTrace(byName))
    } else ZStream.unit
}

private class CatsSemigroupK[R, E] extends SemigroupK[ZStream[R, E, *]] {
  override final def combineK[A](a: ZStream[R, E, A], b: ZStream[R, E, A]): ZStream[R, E, A] =
    a.++(b)(CoreTracer.newTrace)
}

private class CatsMonoidK[R, E] extends CatsSemigroupK[R, E] with MonoidK[ZStream[R, E, *]] {
  override final def empty[A]: ZStream[R, E, A] = ZStream.empty(CoreTracer.newTrace)
}

private trait CatsBifunctor[R] extends Bifunctor[ZStream[R, *, *]] {
  override final def bimap[A, B, C, D](fab: ZStream[R, A, B])(f: A => C, g: B => D): ZStream[R, C, D] =
    fab.mapBoth(f, g)(implicitly[CanFail[A]], InteropTracer.newTrace(f))
}

private class CatsParallel[R, E](final override val monad: Monad[ZStream[R, E, *]]) extends Parallel[ZStream[R, E, *]] {

  final override type F[A] = ParStream[R, E, A]

  final override val applicative: Applicative[ParStream[R, E, *]] =
    new CatsParApplicative[R, E]

  final override val sequential: ParStream[R, E, *] ~> ZStream[R, E, *] =
    new (ParStream[R, E, *] ~> ZStream[R, E, *]) {
      def apply[A](fa: ParStream[R, E, A]): ZStream[R, E, A] = Par.unwrap(fa)
    }

  final override val parallel: ZStream[R, E, *] ~> ParStream[R, E, *] =
    new (ZStream[R, E, *] ~> ParStream[R, E, *]) {
      def apply[A](fa: ZStream[R, E, A]): ParStream[R, E, A] = Par(fa)
    }
}

private class CatsParApplicative[R, E] extends CommutativeApplicative[ParStream[R, E, *]] {

  final override def pure[A](x: A): ParStream[R, E, A] =
    Par(ZStream.succeed(x)(CoreTracer.newTrace))

  final override def map2[A, B, Z](fa: ParStream[R, E, A], fb: ParStream[R, E, B])(
    f: (A, B) => Z
  ): ParStream[R, E, Z] = {
    implicit def tracer: Trace = InteropTracer.newTrace(f)

    Par(Par.unwrap(fa).zipWith(Par.unwrap(fb))(f))
  }

  final override def ap[A, B](ff: ParStream[R, E, A => B])(fa: ParStream[R, E, A]): ParStream[R, E, B] = {
    implicit def tracer: Trace = CoreTracer.newTrace

    Par(Par.unwrap(ff).zipWith(Par.unwrap(fa))(_(_)))
  }

  final override def product[A, B](fa: ParStream[R, E, A], fb: ParStream[R, E, B]): ParStream[R, E, (A, B)] = {
    implicit def tracer: Trace = CoreTracer.newTrace

    Par(Par.unwrap(fa).zip(Par.unwrap(fb)))
  }

  final override def map[A, B](fa: ParStream[R, E, A])(f: A => B): ParStream[R, E, B] = {
    implicit def tracer: Trace = InteropTracer.newTrace(f)

    Par(Par.unwrap(fa).map(f))
  }

  final override def unit: ParStream[R, E, Unit] =
    Par(ZStream.unit)
}
