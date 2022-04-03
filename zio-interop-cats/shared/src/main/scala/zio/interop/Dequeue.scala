/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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
import zio.{ Dequeue as ZDequeue, Runtime, ZTraceElement }

/**
 * A queue that can only be dequeued.
 */
trait Dequeue[F[+_], +A] extends Serializable {

  /**
   * @see [[Dequeue.awaitShutdown]]
   */
  def awaitShutdown(implicit trace: ZTraceElement): F[Unit]

  /**
   * @see [[Dequeue.capacity]]
   */
  def capacity: Int

  /**
   * @see [[Dequeue.isEmpty]]
   */
  def isEmpty(implicit trace: ZTraceElement): F[Boolean]

  /**
   * @see [[Dequeue.isFull]]
   */
  def isFull(implicit trace: ZTraceElement): F[Boolean]

  /**
   * @see [[Dequeue.isShutdown]]
   */
  def isShutdown(implicit trace: ZTraceElement): F[Boolean]

  /**
   * @see [[Dequeue.poll]]
   */
  def poll(implicit trace: ZTraceElement): F[Option[A]]

  /**
   * @see [[Dequeue.shutdown]]
   */
  def shutdown(implicit trace: ZTraceElement): F[Unit]

  /**
   * @see [[Dequeue.size]]
   */
  def size(implicit trace: ZTraceElement): F[Int]

  /**
   * @see [[Dequeue.take]]
   */
  def take(implicit trace: ZTraceElement): F[A]

  /**
   * @see [[Dequeue.takeAll]]
   */
  def takeAll(implicit trace: ZTraceElement): F[List[A]]

  /**
   * @see [[Dequeue.takeBetween]]
   */
  def takeBetween(min: Int, max: Int)(implicit trace: ZTraceElement): F[List[A]]

  /**
   * @see [[Dequeue.takeN]]
   */
  def takeN(n: Int)(implicit trace: ZTraceElement): F[List[A]]

  /**
   * @see [[Dequeue.takeUpTo]]
   */
  def takeUpTo(max: Int)(implicit trace: ZTraceElement): F[List[A]]
}

object Dequeue {

  private[interop] final def apply[F[+_]: Async, A](
    underlying: ZDequeue[A]
  )(implicit runtime: Runtime[Any]): Dequeue[F, A] =
    new Dequeue[F, A] {
      def awaitShutdown(implicit trace: ZTraceElement): F[Unit]                      =
        underlying.awaitShutdown.toEffect[F]
      def capacity: Int                                                              =
        underlying.capacity
      def isEmpty(implicit trace: ZTraceElement): F[Boolean]                         =
        underlying.isEmpty.toEffect[F]
      def isFull(implicit trace: ZTraceElement): F[Boolean]                          =
        underlying.isFull.toEffect[F]
      def isShutdown(implicit trace: ZTraceElement): F[Boolean]                      =
        underlying.isShutdown.toEffect[F]
      def poll(implicit trace: ZTraceElement): F[Option[A]]                          =
        underlying.poll.toEffect[F]
      def shutdown(implicit trace: ZTraceElement): F[Unit]                           =
        underlying.shutdown.toEffect[F]
      def size(implicit trace: ZTraceElement): F[Int]                                =
        underlying.size.toEffect[F]
      def take(implicit trace: ZTraceElement): F[A]                                  =
        underlying.take.toEffect[F]
      def takeAll(implicit trace: ZTraceElement): F[List[A]]                         =
        underlying.takeAll.map(_.toList).toEffect[F]
      def takeBetween(min: Int, max: Int)(implicit trace: ZTraceElement): F[List[A]] =
        underlying.takeBetween(min, max).map(_.toList).toEffect[F]
      def takeN(n: Int)(implicit trace: ZTraceElement): F[List[A]]                   =
        underlying.takeN(n).map(_.toList).toEffect[F]
      def takeUpTo(max: Int)(implicit trace: ZTraceElement): F[List[A]]              =
        underlying.takeUpTo(max).map(_.toList).toEffect[F]
    }
}
