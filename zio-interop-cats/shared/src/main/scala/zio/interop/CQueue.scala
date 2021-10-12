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

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import zio.{ Runtime, ZQueue }

/**
 * @see [[zio.ZQueue]]
 */
sealed abstract class CQueue[F[+_], -A, +B](private val underlying: ZQueue[Any, Any, Throwable, Throwable, A, B]) {

  /**
   * @see [[ZQueue.awaitShutdown]]
   */
  def awaitShutdown: F[Unit]

  /**
   * @see [[ZQueue.capacity]]
   */
  def capacity: Int

  /**
   * @see [[ZQueue.isShutdown]]
   */
  def isShutdown: F[Boolean]

  /**
   * @see [[ZQueue.offer]]
   */
  def offer(a: A): F[Boolean]

  /**
   * @see [[ZQueue.offerAll]]
   */
  def offerAll(as: Iterable[A]): F[Boolean]

  /**
   * @see [[ZQueue.shutdown]]
   */
  def shutdown: F[Unit]

  /**
   * @see [[ZQueue.size]]
   */
  def size: F[Int]

  /**
   * @see [[ZQueue.take]]
   */
  def take: F[B]

  /**
   * @see [[ZQueue.takeAll]]
   */
  def takeAll: F[List[B]]

  /**
   * @see [[ZQueue.takeUpTo]]
   */
  def takeUpTo(max: Int): F[List[B]]

  /**
   * @see [[ZQueue.&&]]
   */
  @deprecated("use ZStream", "2.0.0")
  def &&[A0 <: A, C](that: CQueue[F, A0, C]): CQueue[F, A0, (B, C)]

  /**
   * @see [[ZQueue.both]]
   */
  @deprecated("use ZStream", "2.0.0")
  def both[A0 <: A, C](that: CQueue[F, A0, C]): CQueue[F, A0, (B, C)]

  /**
   * @see [[ZQueue.bothWith]]
   */
  @deprecated("use ZStream", "2.0.0")
  def bothWith[A0 <: A, C, D](that: CQueue[F, A0, C])(f: (B, C) => D): CQueue[F, A0, D]

  /**
   * @see [[ZQueue.bothWithM]]
   */
  @deprecated("use ZStream", "2.0.0")
  def bothWithM[A0 <: A, C, D](that: CQueue[F, A0, C])(f: (B, C) => F[D]): CQueue[F, A0, D]

  /**
   * @see [[ZQueue.contramap]]
   */
  def contramap[C](f: C => A): CQueue[F, C, B]

  /**
   * @see [[ZQueue.contramapM]]
   */
  def contramapM[C](f: C => F[A]): CQueue[F, C, B]

  /**
   * @see [[ZQueue.filterInput]]
   */
  def filterInput[A0 <: A](f: A0 => Boolean): CQueue[F, A0, B]

  /**
   * @see [[ZQueue.filterInputM]]
   */
  def filterInputM[A0 <: A](f: A0 => F[Boolean]): CQueue[F, A0, B]

  /**
   * @see [[ZQueue.map]]
   */
  def map[C](f: B => C): CQueue[F, A, C]

  /**
   * @see [[ZQueue.mapM]]
   */
  def mapM[C](f: B => F[C]): CQueue[F, A, C]

  /**
   * @see [[ZQueue.poll]]
   */
  def poll: F[Option[B]]
}

object CQueue {

  /**
   * @see ZioQueue.bounded
   */
  final def bounded[F[+_]: Async: Dispatcher, A](capacity: Int)(implicit R: Runtime[Any]): F[Queue[F, A]] =
    ZQueue.bounded[A](capacity).map(CQueue(_)).toEffect[F]

  /**
   * @see ZioQueue.dropping
   */
  final def dropping[F[+_]: Async: Dispatcher, A](capacity: Int)(implicit R: Runtime[Any]): F[Queue[F, A]] =
    ZQueue.dropping[A](capacity).map(CQueue(_)).toEffect[F]

  /**
   * @see ZioQueue.sliding
   */
  final def sliding[F[+_]: Async: Dispatcher, A](capacity: Int)(implicit R: Runtime[Any]): F[Queue[F, A]] =
    ZQueue.sliding[A](capacity).map(CQueue(_)).toEffect[F]

  /**
   * @see ZioQueue.unbounded
   */
  final def unbounded[F[+_]: Async: Dispatcher, A](implicit R: Runtime[Any]): F[Queue[F, A]] =
    ZQueue.unbounded[A].map(CQueue(_)).toEffect[F]

  private[interop] final def apply[F[+_]: Async: Dispatcher, A, B](
    underlying: ZQueue[Any, Any, Throwable, Throwable, A, B]
  )(implicit runtime: Runtime[Any]): CQueue[F, A, B] =
    new CQueue[F, A, B](underlying) {
      val awaitShutdown: F[Unit]                                       =
        underlying.awaitShutdown.toEffect[F]
      def capacity: Int                                                =
        underlying.capacity
      val isShutdown: F[Boolean]                                       =
        underlying.isShutdown.toEffect[F]
      def offer(a: A): F[Boolean]                                      =
        underlying.offer(a).toEffect[F]
      def offerAll(as: Iterable[A]): F[Boolean]                        =
        underlying.offerAll(as).toEffect[F]
      val shutdown: F[Unit]                                            =
        underlying.shutdown.toEffect[F]
      val size: F[Int]                                                 =
        underlying.size.toEffect[F]
      val take: F[B]                                                   =
        underlying.take.toEffect[F]
      val takeAll: F[List[B]]                                          =
        underlying.takeAll.toEffect[F]
      def takeUpTo(max: Int): F[List[B]]                               =
        underlying.takeUpTo(max).toEffect[F]
      @deprecated("use ZStream", "2.0.0")
      def &&[A0 <: A, C](that: CQueue[F, A0, C]): CQueue[F, A0, (B, C)] =
        CQueue(underlying && that.underlying)
      @deprecated("use ZStream", "2.0.0")
      def both[A0 <: A, C](that: CQueue[F, A0, C]): CQueue[F, A0, (B, C)] =
        CQueue(underlying.both(that.underlying))
      @deprecated("use ZStream", "2.0.0")
      def bothWith[A0 <: A, C, D](that: CQueue[F, A0, C])(f: (B, C) => D): CQueue[F, A0, D] =
        CQueue(underlying.bothWith(that.underlying)(f))
      @deprecated("use ZStream", "2.0.0")
      def bothWithM[A0 <: A, C, D](that: CQueue[F, A0, C])(f: (B, C) => F[D]): CQueue[F, A0, D] =
        CQueue(underlying.bothWithM(that.underlying)((b, c) => fromEffect(f(b, c))))
      def contramap[C](f: C => A): CQueue[F, C, B]                     =
        CQueue(underlying.contramap(f))
      def contramapM[C](f: C => F[A]): CQueue[F, C, B]                 =
        CQueue(underlying.contramapM(c => fromEffect(f(c))))
      def filterInput[A0 <: A](f: A0 => Boolean): CQueue[F, A0, B]     =
        CQueue(underlying.filterInput(f))
      def filterInputM[A0 <: A](f: A0 => F[Boolean]): CQueue[F, A0, B] =
        CQueue(underlying.filterInputM((a0: A0) => fromEffect(f(a0))))
      def map[C](f: B => C): CQueue[F, A, C]                           =
        CQueue(underlying.map(f))
      def mapM[C](f: B => F[C]): CQueue[F, A, C]                       =
        CQueue(underlying.mapM(b => fromEffect(f(b))))
      val poll: F[Option[B]]                                           =
        underlying.poll.toEffect[F]
    }
}
