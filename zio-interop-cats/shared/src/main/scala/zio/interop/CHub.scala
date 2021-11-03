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
import cats.effect.std.Dispatcher
import zio.interop.catz.zManagedSyntax
import zio.{ Runtime, ZDequeue, ZEnqueue, ZHub, ZQueue, ZTraceElement }

/**
 * A `CHub[F, A, B]` is an asynchronous message hub. Publishers can publish
 * messages of type `A` to the hub and subscribers can subscribe to take
 * messages of type `B` from the hub within the context of the effect `F`.
 */
sealed abstract class CHub[F[+_], -A, +B] extends Serializable {

  /**
   * Waits for the hub to be shut down.
   */
  def awaitShutdown(implicit trace: ZTraceElement): F[Unit]

  /**
   * The maximum capacity of the hub.
   */
  def capacity: Int

  /**
   * Checks whether the hub is shut down.
   */
  def isShutdown(implicit trace: ZTraceElement): F[Boolean]

  /**
   * Publishes a message to the hub, returning whether the message was
   * published to the hub.
   */
  def publish(a: A)(implicit trace: ZTraceElement): F[Boolean]

  /**
   * Publishes all of the specified messages to the hub, returning whether
   * they were published to the hub.
   */
  def publishAll(as: Iterable[A])(implicit trace: ZTraceElement): F[Boolean]

  /**
   * Shuts down the hub.
   */
  def shutdown(implicit trace: ZTraceElement): F[Unit]

  /**
   * The current number of messages in the hub.
   */
  def size(implicit trace: ZTraceElement): F[Int]

  /**
   * Subscribes to receive messages from the hub. The resulting subscription
   * can be evaluated multiple times within the scope of the resource to take a
   * message from the hub each time.
   */
  def subscribe(implicit trace: ZTraceElement): Resource[F, Dequeue[F, B]]

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
  def bounded[F[+_]: Async: Dispatcher, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: ZTraceElement): F[Hub[F, A]] =
    ZHub.bounded[A](requestedCapacity).map(CHub(_)).toEffect[F]

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[F[+_]: Async: Dispatcher, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: ZTraceElement): F[Hub[F, A]] =
    ZHub.dropping[A](requestedCapacity).map(CHub(_)).toEffect[F]

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[F[+_]: Async: Dispatcher, A](
    requestedCapacity: Int
  )(implicit runtime: Runtime[Any], trace: ZTraceElement): F[Hub[F, A]] =
    ZHub.sliding[A](requestedCapacity).map(CHub(_)).toEffect[F]

  /**
   * Creates an unbounded hub.
   */
  def unbounded[F[+_]: Async: Dispatcher, A](implicit runtime: Runtime[Any], trace: ZTraceElement): F[Hub[F, A]] =
    ZHub.unbounded[A].map(CHub(_)).toEffect[F]

  private def apply[F[+_]: Async: Dispatcher, A, B](
    hub: ZHub[Any, Any, Throwable, Throwable, A, B]
  )(implicit runtime: Runtime[Any]): CHub[F, A, B] =
    new CHub[F, A, B] { self =>
      def awaitShutdown(implicit trace: ZTraceElement): F[Unit]                  =
        hub.awaitShutdown.toEffect[F]
      def capacity: Int                                                          =
        hub.capacity
      def isShutdown(implicit trace: ZTraceElement): F[Boolean]                  =
        hub.isShutdown.toEffect[F]
      def publish(a: A)(implicit trace: ZTraceElement): F[Boolean]               =
        hub.publish(a).toEffect[F]
      def publishAll(as: Iterable[A])(implicit trace: ZTraceElement): F[Boolean] =
        hub.publishAll(as).toEffect[F]
      def shutdown(implicit trace: ZTraceElement): F[Unit]                       =
        hub.shutdown.toEffect[F]
      def size(implicit trace: ZTraceElement): F[Int]                            =
        hub.size.toEffect[F]
      def subscribe(implicit trace: ZTraceElement): Resource[F, Dequeue[F, B]]   =
        hub.subscribe.map(Dequeue[F, B](_)).toResource[F]
      def contramap[C](f: C => A): CHub[F, C, B]                                 =
        CHub(hub.contramap(f))
      def contramapM[C](f: C => F[A]): CHub[F, C, B]                             =
        CHub(hub.contramapZIO(c => fromEffect(f(c))))
      def dimap[C, D](f: C => A, g: B => D): CHub[F, C, D]                       =
        CHub(hub.dimap(f, g))
      def dimapM[C, D](f: C => F[A], g: B => F[D]): CHub[F, C, D]                =
        CHub(hub.dimapZIO(c => fromEffect(f(c)), b => fromEffect(g(b))))
      def filterInput[A1 <: A](f: A1 => Boolean): CHub[F, A1, B]                 =
        CHub(hub.filterInput(f))
      def filterInputM[A1 <: A](f: A1 => F[Boolean]): CHub[F, A1, B]             =
        CHub(hub.filterInputZIO(a => fromEffect(f(a))))
      def filterOutput(f: B => Boolean): CHub[F, A, B]                           =
        CHub(hub.filterOutput(f))
      def filterOutputM(f: B => F[Boolean]): CHub[F, A, B]                       =
        CHub(hub.filterOutputZIO(a => fromEffect(f(a))))
      def map[C](f: B => C): CHub[F, A, C]                                       =
        CHub(hub.map(f))
      def mapM[C](f: B => F[C]): CHub[F, A, C]                                   =
        CHub(hub.mapZIO(a => fromEffect(f(a))))
      def toQueue: Enqueue[F, A]                                                 =
        Enqueue(hub.toQueue)
    }

  private def Dequeue[F[+_]: Async: Dispatcher, A](
    dequeue: ZDequeue[Any, Throwable, A]
  )(implicit runtime: Runtime[Any]): Dequeue[F, A] =
    CQueue[F, Nothing, A](dequeue.asInstanceOf[ZQueue[Any, Any, Throwable, Throwable, Nothing, A]])

  private def Enqueue[F[+_]: Async: Dispatcher, A](
    enqueue: ZEnqueue[Any, Throwable, A]
  )(implicit runtime: Runtime[Any]): Enqueue[F, A] =
    CQueue[F, A, Nothing](enqueue.asInstanceOf[ZQueue[Any, Any, Throwable, Throwable, A, Nothing]])
}
