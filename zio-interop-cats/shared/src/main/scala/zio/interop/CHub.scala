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
import zio.{ Runtime, ZDequeue, ZEnqueue, ZHub, ZQueue }
import zio.interop.catz._

/**
 * A `CHub[F, A, B]` is an asynchronous message hub. Publishers can publish
 * messages of type `A` to the hub and subscribers can subscribe to take
 * messages of type `B` from the hub within the context of the effect `F`.
 */
sealed abstract class CHub[F[+_], -A, +B] extends Serializable {

  /**
   * Waits for the hub to be shut down.
   */
  def awaitShutdown: F[Unit]

  /**
   * The maximum capacity of the hub.
   */
  def capacity: Int

  /**
   * Checks whether the hub is shut down.
   */
  def isShutdown: F[Boolean]

  /**
   * Publishes a message to the hub, returning whether the message was
   * published to the hub.
   */
  def publish(a: A): F[Boolean]

  /**
   * Publishes all of the specified messages to the hub, returning whether
   * they were published to the hub.
   */
  def publishAll(as: Iterable[A]): F[Boolean]

  /**
   * Shuts down the hub.
   */
  def shutdown: F[Unit]

  /**
   * The current number of messages in the hub.
   */
  def size: F[Int]

  /**
   * Subscribes to receive messages from the hub. The resulting subscription
   * can be evaluated multiple times within the scope of the resource to take a
   * message from the hub each time.
   */
  def subscribe: Resource[F, Dequeue[F, B]]

  /**
   * Transforms messages published to the hub using the specified function.
   */
  def contramap[C](f: C => A): CHub[F, C, B]

  /**
   * Transforms messages published to the hub using the specified effectual
   * function.
   */
  def contramapM[C](f: C => F[A]): CHub[F, C, B]

  /**
   * Transforms messages published to and taken from the hub using the
   * specified functions.
   */
  def dimap[C, D](f: C => A, g: B => D): CHub[F, C, D]

  /**
   * Transforms messages published to and taken from the hub using the
   * specified effectual functions.
   */
  def dimapM[C, D](
    f: C => F[A],
    g: B => F[D]
  ): CHub[F, C, D]

  /**
   * Filters messages published to the hub using the specified function.
   */
  def filterInput[A1 <: A](f: A1 => Boolean): CHub[F, A1, B]

  /**
   * Filters messages published to the hub using the specified effectual
   * function.
   */
  def filterInputM[A1 <: A](
    f: A1 => F[Boolean]
  ): CHub[F, A1, B]

  /**
   * Filters messages taken from the hub using the specified function.
   */
  def filterOutput(f: B => Boolean): CHub[F, A, B]

  /**
   * Filters messages taken from the hub using the specified effectual
   * function.
   */
  def filterOutputM(
    f: B => F[Boolean]
  ): CHub[F, A, B]

  /**
   * Transforms messages taken from the hub using the specified function.
   */
  def map[C](f: B => C): CHub[F, A, C]

  /**
   * Transforms messages taken from the hub using the specified effectual
   * function.
   */
  def mapM[C](f: B => F[C]): CHub[F, A, C]

  /**
   * Views the hub as a queue that can only be written to.
   */
  def toQueue: Enqueue[F, A]
}

object CHub {

  /**
   * Creates a bounded hub with the back pressure strategy. The hub will retain
   * messages until they have been taken by all subscribers, applying back
   * pressure to publishers if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def bounded[F[+_]: Effect, A](requestedCapacity: Int)(implicit runtime: Runtime[Any]): F[Hub[F, A]] =
    toEffect(ZHub.bounded[A](requestedCapacity).map(hub => CHub(hub)))

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[F[+_]: Effect, A](requestedCapacity: Int)(implicit runtime: Runtime[Any]): F[Hub[F, A]] =
    toEffect(ZHub.dropping[A](requestedCapacity).map(hub => CHub(hub)))

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[F[+_]: Effect, A](requestedCapacity: Int)(implicit runtime: Runtime[Any]): F[Hub[F, A]] =
    toEffect(ZHub.sliding[A](requestedCapacity).map(hub => CHub(hub)))

  /**
   * Creates an unbounded hub.
   */
  def unbounded[F[+_]: Effect, A](implicit runtime: Runtime[Any]): F[Hub[F, A]] =
    toEffect(ZHub.unbounded[A].map(hub => CHub(hub)))

  private def apply[F[+_]: Effect, A, B](
    hub: ZHub[Any, Any, Throwable, Throwable, A, B]
  )(implicit runtime: Runtime[Any]): CHub[F, A, B] =
    new CHub[F, A, B] { self =>
      def awaitShutdown: F[Unit] =
        toEffect(hub.awaitShutdown)
      def capacity: Int =
        hub.capacity
      def isShutdown: F[Boolean] =
        toEffect(hub.isShutdown)
      def publish(a: A): F[Boolean] =
        toEffect(hub.publish(a))
      def publishAll(as: Iterable[A]): F[Boolean] =
        toEffect(hub.publishAll(as))
      def shutdown: F[Unit] =
        toEffect(hub.shutdown)
      def size: F[Int] =
        toEffect(hub.size)
      def subscribe: Resource[F, Dequeue[F, B]] =
        hub.subscribe.map(dequeue => Dequeue[F, B](dequeue)).toResource[F]
      def contramap[C](f: C => A): CHub[F, C, B] =
        CHub(hub.contramap(f))
      def contramapM[C](f: C => F[A]): CHub[F, C, B] =
        CHub(hub.contramapZIO(c => fromEffect(f(c))))
      def dimap[C, D](f: C => A, g: B => D): CHub[F, C, D] =
        CHub(hub.dimap(f, g))
      def dimapM[C, D](f: C => F[A], g: B => F[D]): CHub[F, C, D] =
        CHub(hub.dimapZIO(c => fromEffect(f(c)), b => fromEffect(g(b))))
      def filterInput[A1 <: A](f: A1 => Boolean): CHub[F, A1, B] =
        CHub(hub.filterInput(f))
      def filterInputM[A1 <: A](f: A1 => F[Boolean]): CHub[F, A1, B] =
        CHub(hub.filterInputZIO(a => fromEffect(f(a))))
      def filterOutput(f: B => Boolean): CHub[F, A, B] =
        CHub(hub.filterOutput(f))
      def filterOutputM(f: B => F[Boolean]): CHub[F, A, B] =
        CHub(hub.filterOutputZIO(a => fromEffect(f(a))))
      def map[C](f: B => C): CHub[F, A, C] =
        CHub(hub.map(f))
      def mapM[C](f: B => F[C]): CHub[F, A, C] =
        CHub(hub.mapZIO(a => fromEffect(f(a))))
      def toQueue: Enqueue[F, A] =
        Enqueue(hub.toQueue)
    }

  private def Dequeue[F[+_], A](dequeue: ZDequeue[Any, Throwable, A]): Dequeue[F, A] =
    new Dequeue(dequeue.asInstanceOf[ZQueue[Any, Any, Throwable, Throwable, Nothing, A]])

  private def Enqueue[F[+_], A](enqueue: ZEnqueue[Any, Throwable, A]): Enqueue[F, A] =
    new Enqueue(enqueue.asInstanceOf[ZQueue[Any, Any, Throwable, Throwable, A, Nothing]])
}
