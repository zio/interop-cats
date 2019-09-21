package zio.stream.interop

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

import zio.interop.test.CatsTestFunctions

import cats._
import cats.arrow._
import zio._
import zio.stream._

object catz extends CatsInstances

abstract class CatsInstances extends CatsInstances1 {
  implicit def alternativeInstance[R, E]: Alternative[ZStream[R, E, *]] =
    new CatsAlternative[R, E]
}

abstract class CatsInstances1 extends CatsInstances2 {
  implicit def monoidKInstance[R, E]: MonoidK[ZStream[R, E, *]] =
    new CatsMonoidK[R, E]

  implicit def bifunctorInstance[R]: Bifunctor[ZStream[R, *, *]] =
    new CatsBifunctor[R] {}

  implicit def zioArrowInstance[E]: ArrowChoice[ZStream[*, E, *]] = new CatsArrow[E]
}

sealed abstract class CatsInstances2 extends CatsInstances3 {
  implicit def semigroupKInstance[R, E: Semigroup]: SemigroupK[ZStream[R, E, *]] =
    new CatsSemigroupK[R, E]
}

sealed abstract class CatsInstances3 {
  implicit def monadErrorInstance[R, E]: MonadError[ZStream[R, E, *], E] =
    new CatsMonadError[R, E]
}

private class CatsAlternative[R, E] extends CatsMonoidK[R, E] with CatsApplicative[R, E]

private class CatsMonadError[R, E] extends CatsMonad[R, E] with MonadError[ZStream[R, E, *], E] {
  override final def handleErrorWith[A](fa: ZStream[R, E, A])(f: E => ZStream[R, E, A]): ZStream[R, E, A] =
    fa.catchAll(f)
  override final def raiseError[A](e: E): ZStream[R, E, A] = ZStream.fail(e)

  override def attempt[A](fa: ZStream[R, E, A]): ZIO[R, E, Either[E, A]] = fa.either
}

private class CatsMonad[R, E]
    extends CatsApplicative[R, E]
    with Monad[ZStream[R, E, *]]
    with StackSafeMonad[ZStream[R, E, *]] {
  override final def flatMap[A, B](fa: ZStream[R, E, A])(f: A => ZStream[R, E, B]): ZStream[R, E, B] = fa.flatMap(f)

  override final def widen[A, B >: A](fa: ZStream[R, E, A]): ZStream[R, E, B] = fa
  override final def map2[A, B, Z](fa: ZStream[R, E, A], fb: ZStream[R, E, B])(f: (A, B) => Z): ZStream[R, E, Z] =
    fa.zipWith(fb) { case (Some(a), Some(b)) => Some(f(a, b)); case _ => None }
  override final def as[A, B](fa: ZStream[R, E, A], b: B): ZStream[R, E, B] = fa.as(b)
}

private trait CatsApplicative[R, E] extends Applicative[ZStream[R, E, *]] {
  override final def pure[A](a: A): ZStream[R, E, A]                              = ZStream.succeed(a)
  override final def map[A, B](fa: ZStream[R, E, A])(f: A => B): ZStream[R, E, B] = fa.map(f)

  override final def unit: ZStream[R, E, Unit] = ZStream.unit
  override final def whenA[A](cond: Boolean)(f: => ZStream[R, E, A]): ZStream[R, E, Unit] =
    ZStream.fromEffect(ZIO.effectTotal(f).when(cond))
}

private class CatsSemigroupK[R, E] extends SemigroupK[ZStream[R, E, *]] {
  override final def combineK[A](a: ZStream[R, E, A], b: ZStream[R, E, A]): ZStream[R, E, A] = a ++ b
}

private class CatsMonoidK[R, E] extends CatsSemigroupK[R, E] with MonoidK[ZStream[R, E, *]] {
  override final def empty[A]: ZStream[R, E, A] = ZStream.empty
}

private class CatsBifunctor[R] extends Bifunctor[ZStream[R, *, *]] {
  override final def bimap[A, B, C, D](fab: ZStream[R, A, B])(f: A => C, g: B => D): ZStream[R, C, D] =
    fab.bimap(f, g)
}

private class CatsArrow[E] extends ArrowChoice[ZStream[*, E, *]] {
  final override def lift[A, B](f: A => B): ZStream[A, E, B]                                      = ZStream.fromEffect(ZIO.fromFunction(f))
  final override def compose[A, B, C](f: ZStream[B, E, C], g: ZStream[A, E, B]): ZStream[A, E, C] = g.flatMap(f.provide)
  final override def id[A]: ZStream[A, E, A]                                                      = ZStream.fromEffect(ZIO.environment)
  final override def dimap[A, B, C, D](fab: ZStream[A, E, B])(f: C => A)(g: B => D): ZStream[C, E, D] =
    fab.provideSome(f).map(g)

  def choose[A, B, C, D](f: ZStream[A, E, C])(g: ZStream[B, E, D]): ZStream[Either[A, B], E, Either[C, D]] =
    ZStream
      .fromEffect(ZIO.environment[Either[A, B]])
      .flatMap(_.fold(f.provide(_).map(Left(_)), g.provide(_).map(Right(_))))

  final override def first[A, B, C](fa: ZStream[A, E, B]): ZStream[(A, C), E, (B, C)] =
    ZStream.fromEffect(ZIO.environment[(A, C)]).flatMap { case (a, c) => fa.provide(a).map((_, c)) }
  final override def second[A, B, C](fa: ZStream[A, E, B]): ZStream[(C, A), E, (C, B)] =
    ZStream.fromEffect(ZIO.environment[(C, A)]).flatMap { case (c, a) => fa.provide(a).map((c, _)) }
  final override def split[A, B, C, D](f: ZStream[A, E, B], g: ZStream[C, E, D]): ZStream[(A, C), E, (B, D)] =
    ZStream.fromEffect(ZIO.environment[(A, C)]).flatMap { case (a, c) => f.provide(a).zip(g.provide(c)) }
  final override def merge[A, B, C](f: ZStream[A, E, B], g: ZStream[A, E, C]): ZStream[A, E, (B, C)] = f.zip(g)

  final override def lmap[A, B, C](fab: ZStream[A, E, B])(f: C => A): ZStream[C, E, B] = fab.provideSome(f)
  final override def rmap[A, B, C](fab: ZStream[A, E, B])(f: B => C): ZStream[A, E, C] = fab.map(f)

  final override def choice[A, B, C](f: ZStream[A, E, C], g: ZStream[B, E, C]): ZStream[Either[A, B], E, C] =
    ZStream.fromEffect(ZIO.environment[Either[A, B]]).flatMap(_.fold(f.provide, g.provide))
}
