/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import cats.effect.kernel.{ Async, Resource }
import zio.interop.catz.scopedSyntax
import zio.{ Hub as ZHub, Runtime, Trace }

/**
 * A `Hub[F, A]` is an asynchronous message hub. Publishers can publish
 * messages to the hub and subscribers can subscribe to take messages of from
 * the hub within the context of the effect `F`.
 */
abstract class Hub[F[+_], A] extends Enqueue[F, A] with Serializable {

  /**
   * Checks whether the hub is shut down.
   */
  def isShutdown(implicit trace: Trace): F[Boolean]

  /**
   * Publishes a message to the hub, returning whether the message was
   * published to the hub.
   */
  def publish(a: A)(implicit trace: Trace): F[Boolean]

  /**
   * Publishes all of the specified messages to the hub, returning whether
   * they were published to the hub.
   */
  def publishAll(as: Iterable[A])(implicit trace: Trace): F[Boolean]

  /**
   * Subscribes to receive messages from the hub. The resulting subscription
   * can be evaluated multiple times within the scope of the resource to take a
   * message from the hub each time.
   */
  def subscribe(implicit trace: Trace): Resource[F, Dequeue[F, A]]
}

object Hub {

  /**
   * Creates a bounded hub with the back pressure strategy. The hub will retain
   * messages until they have been taken by all subscribers, applying back
   * pressure to publishers if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def bounded[F[+_]: Async, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    ZHub.bounded[A](requestedCapacity).map(Hub(_)).toEffect[F]

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[F[+_]: Async, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    ZHub.dropping[A](requestedCapacity).map(Hub(_)).toEffect[F]

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[F[+_]: Async, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    ZHub.sliding[A](requestedCapacity).map(Hub(_)).toEffect[F]

  /**
   * Creates an unbounded hub.
   */
  def unbounded[F[+_]: Async, A](implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    ZHub.unbounded[A].map(Hub(_)).toEffect[F]

  private def apply[F[+_]: Async, A](
    hub: ZHub[A]
  )(implicit runtime: Runtime[Any]): Hub[F, A] =
    new Hub[F, A] { self =>
      def awaitShutdown(implicit trace: Trace): F[Unit]                  =
        hub.awaitShutdown.toEffect[F]
      def capacity: Int                                                  =
        hub.capacity
      def isEmpty(implicit trace: Trace): F[Boolean]                     =
        hub.isEmpty.toEffect[F]
      def isFull(implicit trace: Trace): F[Boolean]                      =
        hub.isFull.toEffect[F]
      def isShutdown(implicit trace: Trace): F[Boolean]                  =
        hub.isShutdown.toEffect[F]
      def publish(a: A)(implicit trace: Trace): F[Boolean]               =
        hub.publish(a).toEffect[F]
      def publishAll(as: Iterable[A])(implicit trace: Trace): F[Boolean] =
        hub.publishAll(as).map(_.isEmpty).toEffect[F]
      def shutdown(implicit trace: Trace): F[Unit]                       =
        hub.shutdown.toEffect[F]
      def size(implicit trace: Trace): F[Int]                            =
        hub.size.toEffect[F]
      def subscribe(implicit trace: Trace): Resource[F, Dequeue[F, A]]   =
        Resource.scoped[F, Any, Dequeue[F, A]](hub.subscribe.map(Dequeue[F, A](_)))
      def offer(a: A)(implicit trace: Trace): F[Boolean]                 =
        publish(a)
      def offerAll(as: Iterable[A])(implicit trace: Trace): F[Boolean]   =
        publishAll(as)
    }
}
