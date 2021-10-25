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

package zio.interop.stm

import cats.effect.kernel.Async
import zio.{ Runtime, Zippable }
import zio.stm.STM as ZSTM

import scala.util.Try

/**
 * See [[zio.stm.ZSTM]]
 */
final class STM[F[+_], +A] private[stm] (private[stm] val underlying: ZSTM[Throwable, A]) {

  /**
   * See [[zio.stm.ZSTM#<*>]]
   */
  def <*>[B](that: => STM[F, B])(implicit zippable: Zippable[A, B]): STM[F, zippable.Out] =
    this zip that

  /**
   * See [[zio.stm.ZSTM#<*[*]]
   */
  def <*[B](that: => STM[F, B]): STM[F, A] =
    this zipLeft that

  /**
   * See [[zio.stm.ZSTM#*>]]
   */
  def *>[B](that: => STM[F, B]): STM[F, B] =
    this zipRight that

  /**
   * See [[zio.stm.ZSTM#collect]]
   */
  def collect[B](pf: PartialFunction[A, B]): STM[F, B] =
    new STM(underlying.collect(pf))

  /**
   * See [[zio.stm.ZSTM#commit]]
   */
  def commit(implicit R: Runtime[Any], A: Async[F]): F[A] =
    STM.atomically(this)

  def const[B](b: => B): STM[F, B] =
    map(_ => b)

  /**
   * See [[zio.stm.ZSTM#either]]
   */
  def either: STM[F, Either[Throwable, A]] =
    new STM(underlying.either)

  /**
   * See [[zio.stm.ZSTM#withFilter]]
   */
  def filter(f: A => Boolean): STM[F, A]       =
    collect { case a if f(a) => a }

  /**
   * See [[zio.stm.ZSTM#flatMap]]
   */
  def flatMap[B](f: A => STM[F, B]): STM[F, B] =
    new STM(underlying.flatMap(f(_).underlying))

  /**
   * See [[zio.stm.ZSTM.flatten]]
   */
  def flatten[B](implicit ev: A <:< STM[F, B]): STM[F, B] =
    flatMap(ev)

  /**
   * See [[zio.stm.ZSTM#fold]]
   */
  def fold[B](f: Throwable => B, g: A => B): STM[F, B] =
    new STM(underlying.fold(f, g))

  /**
   * See [[zio.stm.ZSTM#foldSTM]]
   */
  def foldM[B](f: Throwable => STM[F, B], g: A => STM[F, B]): STM[F, B] =
    new STM(underlying.foldSTM(f(_).underlying, g(_).underlying))

  /**
   * See [[zio.stm.ZSTM#map]]
   */
  def map[B](f: A => B): STM[F, B] =
    new STM(underlying.map(f))

  /**
   * See [[zio.stm.ZSTM#mapError]]
   */
  def mapError[E1 <: Throwable](f: Throwable => E1): STM[F, A] =
    new STM(underlying.mapError(f))

  /**
   * Switch from effect F to effect G.
   */
  def mapK[G[+_]]: STM[G, A] =
    new STM(underlying)

  /**
   * See [[zio.stm.ZSTM#option]]
   */
  def option: STM[F, Option[A]] =
    fold[Option[A]](_ => None, Some[A])

  /**
   * See [[zio.stm.ZSTM#orElse]]
   */
  def orElse[A1 >: A](that: => STM[F, A1]): STM[F, A1] =
    new STM(underlying.orElse(that.underlying))

  /**
   * See [[zio.stm.ZSTM#orElseEither]]
   */
  def orElseEither[B](that: => STM[F, B]): STM[F, Either[A, B]] =
    this.map(Left[A, B]) orElse that.map(Right[A, B])

  /**
   * See [[zio.stm.ZSTM.unit]]
   */
  def unit: STM[F, Unit] =
    const(())

  /**
   * See [[zio.stm.ZSTM.unit]]
   */
  def void: STM[F, Unit] =
    unit

  /**
   * Same as [[filter]]
   */
  def withFilter(f: A => Boolean): STM[F, A] =
    filter(f)

  /**
   * See [[zio.stm.ZSTM#zip]]
   */
  def zip[B](that: => STM[F, B])(implicit zippable: Zippable[A, B]): STM[F, zippable.Out] =
    zipWith(that)(zippable.zip(_, _))

  /**
   * See [[zio.stm.ZSTM#zipLeft]]
   */
  def zipLeft[B](that: => STM[F, B]): STM[F, A] =
    zipWith(that)((a, _) => a)

  /**
   * See [[zio.stm.ZSTM#zipRight]]
   */
  def zipRight[B](that: => STM[F, B]): STM[F, B] =
    zipWith(that)((_, b) => b)

  /**
   * See [[zio.stm.ZSTM#zipWith]]
   */
  def zipWith[B, C](that: => STM[F, B])(f: (A, B) => C): STM[F, C] =
    flatMap(a => that.map(f(a, _)))
}

object STM {

  final def atomically[F[+_], A](stm: => STM[F, A])(implicit R: Runtime[Any], F: Async[F]): F[A] =
    F.async_ { cb =>
      R.unsafeRunAsyncWith(ZSTM.atomically(stm.underlying)) { exit =>
        cb(exit.toEither)
      }
    }

  final def attempt[F[+_], A](a: => A): STM[F, A] =
    fromTry(Try(a))

  final def check[F[+_]](p: => Boolean): STM[F, Unit] =
    succeed(p).flatMap(p => if (p) STM.unit else retry)

  final def collectAll[F[+_], A](i: Iterable[STM[F, A]]): STM[F, List[A]] =
    new STM(ZSTM.collectAll(i.map(_.underlying).toList))

  final def die[F[+_]](t: => Throwable): STM[F, Nothing] =
    succeed(throw t)

  final def dieMessage[F[+_]](m: String): STM[F, Nothing] =
    die(new RuntimeException(m))

  final def fail[F[+_]](e: Throwable): STM[F, Nothing] =
    new STM(ZSTM.fail(e))

  final def foreach[F[+_], A, B](as: Iterable[A])(f: A => STM[F, B]): STM[F, List[B]] =
    collectAll(as.map(f))

  final def fromEither[F[+_], A](e: Either[Throwable, A]): STM[F, A] =
    new STM(ZSTM.fromEither(e))

  final def fromTry[F[+_], A](a: => Try[A]): STM[F, A] =
    new STM(ZSTM.fromTry(a))

  final def partial[F[+_], A](a: => A): STM[F, A] =
    fromTry(Try(a))

  final def retry[F[+_]]: STM[F, Nothing] =
    new STM(ZSTM.retry)

  final def succeed[F[+_], A](a: => A): STM[F, A] =
    new STM(ZSTM.succeed(a))

  final def unit[F[+_]]: STM[F, Unit] =
    new STM(ZSTM.unit)
}
