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

import cats.effect.{ Effect, Resource }
import zio.{ Runtime, Trace }
import zio.interop.catz._

/**
 * A `CHub[F, A, B]` is an asynchronous message hub. Publishers can publish
 * messages of type `A` to the hub and subscribers can subscribe to take
 * messages of type `B` from the hub within the context of the effect `F`.
 */
sealed abstract class Hub[F[+_], A] extends Serializable {

  /**
   * Waits for the hub to be shut down.
   */
  def awaitShutdown(implicit trace: Trace): F[Unit]

  /**
   * The maximum capacity of the hub.
   */
  def capacity: Int

  /**
   * Checks if this hub is empty.
   */
  def isEmpty(implicit trace: Trace): F[Boolean]

  /**
   * Checks if this hub is full.
   */
  def isFull(implicit trace: Trace): F[Boolean]

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
   * Shuts down the hub.
   */
  def shutdown(implicit trace: Trace): F[Unit]

  /**
   * The current number of messages in the hub.
   */
  def size(implicit trace: Trace): F[Int]

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
  def bounded[F[+_]: Effect, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    toEffect(zio.Hub.bounded[A](requestedCapacity).map(hub => Hub(hub)))

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[F[+_]: Effect, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    toEffect(zio.Hub.dropping[A](requestedCapacity).map(hub => Hub(hub)))

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[F[+_]: Effect, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    toEffect(zio.Hub.sliding[A](requestedCapacity).map(hub => Hub(hub)))

  /**
   * Creates an unbounded hub.
   */
  def unbounded[F[+_]: Effect, A](implicit runtime: Runtime[Any], trace: Trace): F[Hub[F, A]] =
    toEffect(zio.Hub.unbounded[A].map(hub => Hub(hub)))

  private def apply[F[+_]: Effect, A](
    hub: zio.Hub[A]
  )(implicit runtime: Runtime[Any]): Hub[F, A] =
    new Hub[F, A] { self =>
      override def awaitShutdown(implicit trace: Trace): F[Unit] =
        toEffect(hub.awaitShutdown)
      override def capacity: Int =
        hub.capacity
      override def isEmpty(implicit trace: Trace): F[Boolean] = toEffect(hub.isEmpty)
      override def isFull(implicit trace: Trace): F[Boolean]  = toEffect(hub.isFull)
      override def isShutdown(implicit trace: Trace): F[Boolean] =
        toEffect(hub.isShutdown)
      override def publish(a: A)(implicit trace: Trace): F[Boolean] =
        toEffect(hub.publish(a))
      override def publishAll(as: Iterable[A])(implicit trace: Trace): F[Boolean] =
        toEffect(hub.publishAll(as))
      override def shutdown(implicit trace: Trace): F[Unit] =
        toEffect(hub.shutdown)
      override def size(implicit trace: Trace): F[Int] =
        toEffect(hub.size)
      override def subscribe(implicit trace: Trace): Resource[F, Dequeue[F, A]] =
        Resource.scoped[F, Any](hub.subscribe.map(dequeue => Dequeue[F, A](dequeue)))

      private def Dequeue[F[+_], A](dequeue: zio.Dequeue[A]): Dequeue[F, A] =
        new Queue(dequeue.asInstanceOf[zio.Queue[A]])

    }

}
