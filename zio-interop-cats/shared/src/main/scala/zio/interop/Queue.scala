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
import zio.{ Queue as ZQueue, Runtime, Trace }

/**
 * @see [[zio.Queue]]
 */
abstract class Queue[F[+_], A] extends Dequeue[F, A] with Enqueue[F, A]

object Queue {

  /**
   * @see ZioQueue.bounded
   */
  final def bounded[F[+_]: Async, A](
    capacity: Int
  )(implicit R: Runtime[Any], trace: Trace): F[Queue[F, A]] =
    ZQueue.bounded[A](capacity).map(Queue(_)).toEffect[F]

  /**
   * @see ZioQueue.dropping
   */
  final def dropping[F[+_]: Async, A](
    capacity: Int
  )(implicit R: Runtime[Any], trace: Trace): F[Queue[F, A]] =
    ZQueue.dropping[A](capacity).map(Queue(_)).toEffect[F]

  /**
   * @see ZioQueue.sliding
   */
  final def sliding[F[+_]: Async, A](
    capacity: Int
  )(implicit R: Runtime[Any], trace: Trace): F[Queue[F, A]] =
    ZQueue.sliding[A](capacity).map(Queue(_)).toEffect[F]

  /**
   * @see ZioQueue.unbounded
   */
  final def unbounded[F[+_]: Async, A](implicit R: Runtime[Any], trace: Trace): F[Queue[F, A]] =
    ZQueue.unbounded[A].map(Queue(_)).toEffect[F]

  private[interop] final def apply[F[+_]: Async, A](
    underlying: ZQueue[A]
  )(implicit runtime: Runtime[Any]): Queue[F, A] =
    new Queue[F, A] {
      def awaitShutdown(implicit trace: Trace): F[Unit]                          =
        underlying.awaitShutdown.toEffect[F]
      def capacity: Int                                                          =
        underlying.capacity
      def isEmpty(implicit trace: zio.Trace): F[Boolean]                         =
        underlying.isEmpty.toEffect[F]
      def isFull(implicit trace: zio.Trace): F[Boolean]                          =
        underlying.isFull.toEffect[F]
      def isShutdown(implicit trace: Trace): F[Boolean]                          =
        underlying.isShutdown.toEffect[F]
      def offer(a: A)(implicit trace: Trace): F[Boolean]                         =
        underlying.offer(a).toEffect[F]
      def offerAll(as: Iterable[A])(implicit trace: Trace): F[Boolean]           =
        underlying.offerAll(as).map(_.isEmpty).toEffect[F]
      def shutdown(implicit trace: Trace): F[Unit]                               =
        underlying.shutdown.toEffect[F]
      def size(implicit trace: Trace): F[Int]                                    =
        underlying.size.toEffect[F]
      def take(implicit trace: Trace): F[A]                                      =
        underlying.take.toEffect[F]
      def takeAll(implicit trace: Trace): F[List[A]]                             =
        underlying.takeAll.map(_.toList).toEffect[F]
      def takeBetween(min: Int, max: Int)(implicit trace: zio.Trace): F[List[A]] =
        underlying.takeBetween(min, max).map(_.toList).toEffect[F]
      def takeN(n: Int)(implicit trace: zio.Trace): F[List[A]]                   =
        underlying.takeN(n).map(_.toList).toEffect[F]
      def takeUpTo(max: Int)(implicit trace: Trace): F[List[A]]                  =
        underlying.takeUpTo(max).map(_.toList).toEffect[F]
      def poll(implicit trace: Trace): F[Option[A]]                              =
        underlying.poll.toEffect[F]
    }
}
