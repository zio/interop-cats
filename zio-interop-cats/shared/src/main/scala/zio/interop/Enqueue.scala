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
import zio.{ Enqueue as ZEnqueue, Runtime, Trace }

/**
 * A queue that can only be enqueued.
 */
trait Enqueue[F[+_], -A] extends Serializable {

  /**
   * @see [[Enqueue.awaitShutdown]]
   */
  def awaitShutdown(implicit trace: Trace): F[Unit]

  /**
   * @see [[Enqueue.capacity]]
   */
  def capacity: Int

  /**
   * @see [[Enqueue.isEmpty]]
   */
  def isEmpty(implicit trace: Trace): F[Boolean]

  /**
   * @see [[Enqueue.isFull]]
   */
  def isFull(implicit trace: Trace): F[Boolean]

  /**
   * @see [[Enqueue.isShutdown]]
   */
  def isShutdown(implicit trace: Trace): F[Boolean]

  /**
   * @see [[Enqueue.offer]]
   */
  def offer(a: A)(implicit trace: Trace): F[Boolean]

  /**
   * @see [[Enqueue.offerAll]]
   */
  def offerAll(as: Iterable[A])(implicit trace: Trace): F[Boolean]

  /**
   * @see [[Enqueue.shutdown]]
   */
  def shutdown(implicit trace: Trace): F[Unit]

  /**
   * @see [[Enqueue.size]]
   */
  def size(implicit trace: Trace): F[Int]
}

object Enqueue {

  private[interop] final def apply[F[+_]: Async, A](
    underlying: ZEnqueue[A]
  )(implicit runtime: Runtime[Any]): Enqueue[F, A] =
    new Enqueue[F, A] {
      def awaitShutdown(implicit trace: Trace): F[Unit]                =
        underlying.awaitShutdown.toEffect[F]
      def capacity: Int                                                =
        underlying.capacity
      def isEmpty(implicit trace: Trace): F[Boolean]                   =
        underlying.isEmpty.toEffect[F]
      def isFull(implicit trace: Trace): F[Boolean]                    =
        underlying.isFull.toEffect[F]
      def isShutdown(implicit trace: Trace): F[Boolean]                =
        underlying.isShutdown.toEffect[F]
      def offer(a: A)(implicit trace: Trace): F[Boolean]               =
        underlying.offer(a).toEffect[F]
      def offerAll(as: Iterable[A])(implicit trace: Trace): F[Boolean] =
        underlying.offerAll(as).toEffect[F]
      def shutdown(implicit trace: Trace): F[Unit]                     =
        underlying.shutdown.toEffect[F]
      def size(implicit trace: Trace): F[Int]                          =
        underlying.size.toEffect[F]
    }
}
