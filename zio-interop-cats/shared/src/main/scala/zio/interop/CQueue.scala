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
import zio.{ Runtime, ZQueue, ZTraceElement }

/**
 * @see [[zio.ZQueue]]
 */
sealed abstract class CQueue[F[+_], -A, +B](private val underlying: ZQueue[Any, Any, Throwable, Throwable, A, B]) {

  /**
   * @see [[ZQueue.awaitShutdown]]
   */
  def awaitShutdown(implicit trace: ZTraceElement): F[Unit]

  /**
   * @see [[ZQueue.capacity]]
   */
  def capacity: Int

  /**
   * @see [[ZQueue.isShutdown]]
   */
  def isShutdown(implicit trace: ZTraceElement): F[Boolean]

  /**
   * @see [[ZQueue.offer]]
   */
  def offer(a: A)(implicit trace: ZTraceElement): F[Boolean]

  /**
   * @see [[ZQueue.offerAll]]
   */
  def offerAll(as: Iterable[A])(implicit trace: ZTraceElement): F[Boolean]

  /**
   * @see [[ZQueue.shutdown]]
   */
  def shutdown(implicit trace: ZTraceElement): F[Unit]

  /**
   * @see [[ZQueue.size]]
   */
  def size(implicit trace: ZTraceElement): F[Int]

  /**
   * @see [[ZQueue.take]]
   */
  def take(implicit trace: ZTraceElement): F[B]

  /**
   * @see [[ZQueue.takeAll]]
   */
  def takeAll(implicit trace: ZTraceElement): F[List[B]]

  /**
   * @see [[ZQueue.takeUpTo]]
   */
  def takeUpTo(max: Int)(implicit trace: ZTraceElement): F[List[B]]

  /**
   * @see [[ZQueue.contramap]]
   */
  def contramap[C](f: C => A): CQueue[F, C, B]

  /**
   * @see [[ZQueue.contramapZIO]]
   */
  def contramapM[C](f: C => F[A])(implicit trace: ZTraceElement): CQueue[F, C, B]

  /**
   * @see [[ZQueue.filterInput]]
   */
  def filterInput[A0 <: A](f: A0 => Boolean): CQueue[F, A0, B]

  /**
   * @see [[ZQueue.filterInputZIO]]
   */
  def filterInputM[A0 <: A](f: A0 => F[Boolean])(implicit trace: ZTraceElement): CQueue[F, A0, B]

  /**
   * @see [[ZQueue.map]]
   */
  def map[C](f: B => C): CQueue[F, A, C]

  /**
   * @see [[ZQueue.mapZIO]]
   */
  def mapM[C](f: B => F[C])(implicit trace: ZTraceElement): CQueue[F, A, C]

  /**
   * @see [[ZQueue.poll]]
   */
  def poll(implicit trace: ZTraceElement): F[Option[B]]
}

object CQueue {

  /**
   * @see ZioQueue.bounded
   */
  final def bounded[F[+_]: Async: Dispatcher, A](
    capacity: Int
  )(implicit R: Runtime[Any], trace: ZTraceElement): F[Queue[F, A]] =
    ZQueue.bounded[A](capacity).map(CQueue(_)).toEffect[F]

  /**
   * @see ZioQueue.dropping
   */
  final def dropping[F[+_]: Async: Dispatcher, A](
    capacity: Int
  )(implicit R: Runtime[Any], trace: ZTraceElement): F[Queue[F, A]] =
    ZQueue.dropping[A](capacity).map(CQueue(_)).toEffect[F]

  /**
   * @see ZioQueue.sliding
   */
  final def sliding[F[+_]: Async: Dispatcher, A](
    capacity: Int
  )(implicit R: Runtime[Any], trace: ZTraceElement): F[Queue[F, A]] =
    ZQueue.sliding[A](capacity).map(CQueue(_)).toEffect[F]

  /**
   * @see ZioQueue.unbounded
   */
  final def unbounded[F[+_]: Async: Dispatcher, A](implicit R: Runtime[Any], trace: ZTraceElement): F[Queue[F, A]] =
    ZQueue.unbounded[A].map(CQueue(_)).toEffect[F]

  private[interop] final def apply[F[+_]: Async: Dispatcher, A, B](
    underlying: ZQueue[Any, Any, Throwable, Throwable, A, B]
  )(implicit runtime: Runtime[Any]): CQueue[F, A, B] =
    new CQueue[F, A, B](underlying) {
      def awaitShutdown(implicit trace: ZTraceElement): F[Unit]                                       =
        underlying.awaitShutdown.toEffect[F]
      def capacity: Int                                                                               =
        underlying.capacity
      def isShutdown(implicit trace: ZTraceElement): F[Boolean]                                       =
        underlying.isShutdown.toEffect[F]
      def offer(a: A)(implicit trace: ZTraceElement): F[Boolean]                                      =
        underlying.offer(a).toEffect[F]
      def offerAll(as: Iterable[A])(implicit trace: ZTraceElement): F[Boolean]                        =
        underlying.offerAll(as).toEffect[F]
      def shutdown(implicit trace: ZTraceElement): F[Unit]                                            =
        underlying.shutdown.toEffect[F]
      def size(implicit trace: ZTraceElement): F[Int]                                                 =
        underlying.size.toEffect[F]
      def take(implicit trace: ZTraceElement): F[B]                                                   =
        underlying.take.toEffect[F]
      def takeAll(implicit trace: ZTraceElement): F[List[B]]                                          =
        underlying.takeAll.map(_.toList).toEffect[F]
      def takeUpTo(max: Int)(implicit trace: ZTraceElement): F[List[B]]                               =
        underlying.takeUpTo(max).map(_.toList).toEffect[F]
      def contramap[C](f: C => A): CQueue[F, C, B]                                                    =
        CQueue(underlying.contramap(f))
      def contramapM[C](f: C => F[A])(implicit trace: ZTraceElement): CQueue[F, C, B]                 =
        CQueue(underlying.contramapZIO(c => fromEffect(f(c))))
      def filterInput[A0 <: A](f: A0 => Boolean): CQueue[F, A0, B]                                    =
        CQueue(underlying.filterInput(f))
      def filterInputM[A0 <: A](f: A0 => F[Boolean])(implicit trace: ZTraceElement): CQueue[F, A0, B] =
        CQueue(underlying.filterInputZIO((a0: A0) => fromEffect(f(a0))))
      def map[C](f: B => C): CQueue[F, A, C]                                                          =
        CQueue(underlying.map(f))
      def mapM[C](f: B => F[C])(implicit trace: ZTraceElement): CQueue[F, A, C]                       =
        CQueue(underlying.mapZIO(b => fromEffect(f(b))))
      def poll(implicit trace: ZTraceElement): F[Option[B]]                                           =
        underlying.poll.toEffect[F]
    }
}
