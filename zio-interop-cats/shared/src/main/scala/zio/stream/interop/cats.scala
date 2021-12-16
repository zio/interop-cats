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

import cats.*
import cats.effect.kernel.Par.ParallelF
import zio.*
import zio.stream.*

import zio.internal.stacktracer.InteropTracer
import zio.internal.stacktracer.{ Tracer => CoreTracer }

object catz extends CatsInstances {
  object core extends CatsInstances
}

sealed abstract class CatsInstances extends CatsInstances1 {
  implicit def zstreamAlternativeInstance[R, E]: Alternative[ZStream[R, E, _]] =
    new ZStreamAlternative[R, E]
}

sealed abstract class CatsInstances1 extends CatsInstances2 {
  implicit def zstreamMonoidKInstance[R, E]: MonoidK[ZStream[R, E, _]] =
    new ZStreamMonoidK[R, E]

  implicit def zstreamBifunctorInstance[R]: Bifunctor[ZStream[R, _, _]] =
    new ZStreamBifunctor[R] {}
}

sealed abstract class CatsInstances2 extends CatsInstances3 {
  implicit def zstreamParallelInstance[R, E]: Parallel.Aux[ZStream[R, E, _], ParallelF[ZStream[R, E, _], _]] =
    new ZStreamParallel[R, E](zstreamMonadErrorInstance[R, E])
}

sealed abstract class CatsInstances3 {
  implicit def zstreamMonadErrorInstance[R, E]: MonadError[ZStream[R, E, _], E] =
    new ZStreamMonadError[R, E]
}

private class ZStreamAlternative[R, E]
    extends ZStreamMonoidK[R, E]
    with ZStreamApplicative[R, E]
    with Alternative[ZStream[R, E, _]] {
  override type F[A] = ZStream[R, E, A]
}

private class ZStreamMonadError[R, E] extends MonadError[ZStream[R, E, _], E] with StackSafeMonad[ZStream[R, E, _]] {

  override final def handleErrorWith[A](fa: ZStream[R, E, A])(f: E => ZStream[R, E, A]): ZStream[R, E, A] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.catchAll(f)
  }

  override final def raiseError[A](e: E): ZStream[R, E, A] = ZStream.fail(e)(CoreTracer.newTrace)

  override def attempt[A](fa: ZStream[R, E, A]): ZStream[R, E, Either[E, A]] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.either
  }

  override final def flatMap[A, B](fa: ZStream[R, E, A])(f: A => ZStream[R, E, B]): ZStream[R, E, B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.flatMap(f)
  }
  override final def pure[A](a: A): ZStream[R, E, A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZStream.succeed(a)
  }
  override final def map[A, B](fa: ZStream[R, E, A])(f: A => B): ZStream[R, E, B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.map(f)
  }

  override final def widen[A, B >: A](fa: ZStream[R, E, A]): ZStream[R, E, B] = fa
  override final def map2[A, B, Z](fa: ZStream[R, E, A], fb: ZStream[R, E, B])(f: (A, B) => Z): ZStream[R, E, Z] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.crossWith(fb)(f)
  }
  override final def as[A, B](fa: ZStream[R, E, A], b: B): ZStream[R, E, B] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    fa.as(b)
  }
}

private trait ZStreamApplicative[R, E] extends Applicative[ZStream[R, E, _]] {
  type F[A] = ZStream[R, E, A]

  override final def pure[A](a: A): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZStream.succeed(a)
  }

  override final def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fa.map(f)
  }

  override final def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ff.crossWith(fa)(_.apply(_))
  }

  override final def unit: F[Unit] =
    ZStream.unit

  override final def whenA[A](cond: Boolean)(f: => F[A]): F[Unit] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    if (cond) f.as(()) else ZStream.unit
  }
}

private class ZStreamMonoidK[R, E] extends MonoidK[ZStream[R, E, _]] {
  type F[A] = ZStream[R, E, A]

  override final def empty[A]: F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ZStream.empty
  }

  override final def combineK[A](a: F[A], b: F[A]): F[A] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    a ++ b
  }
}

private trait ZStreamBifunctor[R] extends Bifunctor[ZStream[R, _, _]] {
  type F[A, B] = ZStream[R, A, B]

  override final def bimap[A, B, C, D](fab: F[A, B])(f: A => C, g: B => D): F[C, D] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    fab.mapBoth(f, g)
  }
}

private class ZStreamParallel[R, E](final override val monad: Monad[ZStream[R, E, _]])
    extends Parallel[ZStream[R, E, _]] {
  type G[A] = ZStream[R, E, A]
  type F[A] = ParallelF[G, A]

  final override val applicative: Applicative[F] =
    new ZStreamParApplicative[R, E]

  final override val sequential: F ~> G = new (F ~> G) {
    def apply[A](fa: F[A]): G[A] = ParallelF.value(fa)
  }

  final override val parallel: G ~> F = new (G ~> F) {
    def apply[A](fa: G[A]): F[A] = ParallelF(fa)
  }
}

private class ZStreamParApplicative[R, E] extends CommutativeApplicative[ParallelF[ZStream[R, E, _], _]] {
  type G[A] = ZStream[R, E, A]
  type F[A] = ParallelF[G, A]

  final override def pure[A](x: A): F[A] =
    ParallelF[G, A](ZStream.succeed(x)(CoreTracer.newTrace))

  final override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).zipWith(ParallelF.value(fb))(f))
  }

  final override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ParallelF(ParallelF.value(ff).zipWith(ParallelF.value(fa))(_.apply(_)))
  }

  final override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] = {
    implicit def trace: ZTraceElement = CoreTracer.newTrace

    ParallelF(ParallelF.value(fa).zip(ParallelF.value(fb)))
  }

  final override def map[A, B](fa: F[A])(f: A => B): F[B] = {
    implicit def trace: ZTraceElement = InteropTracer.newTrace(f)

    ParallelF(ParallelF.value(fa).map(f))
  }

  final override val unit: F[Unit] =
    ParallelF[G, Unit](ZStream.unit)
}
