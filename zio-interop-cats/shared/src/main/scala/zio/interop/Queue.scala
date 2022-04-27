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

import cats.effect.LiftIO
import zio.{ Queue => ZioQueue, Runtime, UIO, ZEnv, Trace }

sealed trait Dequeue[F[+_], +A] {

  /**
   * @see [[ZQueue.awaitShutdown]]
   */
  def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[ZQueue.capacity]]
   */
  def capacity: Int

  def isEmpty(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  def isFull(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[ZQueue.isShutdown]]
   */
  def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[ZQueue.poll]]
   */
  def poll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Option[A]]

  /**
   * @see [[ZQueue.shutdown]]
   */
  def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[ZQueue.size]]
   */
  def size(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Int]

  /**
   * @see [[ZQueue.take]]
   */
  def take(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[A]

  /**
   * @see [[ZQueue.takeAll]]
   */
  def takeAll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]]

  def takeN(n: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]]

  /**
   * @see [[ZQueue.takeUpTo]]
   */
  def takeUpTo(max: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]]

}

sealed trait Enqueue[F[+_], -A] {

  /**
   * @see [[ZQueue.awaitShutdown]]
   */
  def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[ZQueue.capacity]]
   */
  def capacity: Int

  def isEmpty(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  def isFull(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[ZQueue.isShutdown]]
   */
  def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[ZQueue.offer]]
   */
  def offer(a: A)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[ZQueue.offerAll]]
   */
  def offerAll(as: Iterable[A])(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[ZQueue.shutdown]]
   */
  def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[ZQueue.size]]
   */
  def size(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Int]

}

/**
 * @see [[zio.ZQueue]]
 */
final class Queue[F[+_], A] private[interop] (
  private val underlying: zio.Queue[A]
) extends Dequeue[F, A]
    with Enqueue[F, A] {

  /**
   * @see [[ZQueue.awaitShutdown]]
   */
  override def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit] =
    toEffect(underlying.awaitShutdown)

  /**
   * @see [[ZQueue.capacity]]
   */
  override def capacity: Int = underlying.capacity

  override def isEmpty(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.isEmpty)

  override def isFull(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.isFull)

  /**
   * @see [[ZQueue.isShutdown]]
   */
  override def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.isShutdown)

  /**
   * @see [[ZQueue.offer]]
   */
  override def offer(a: A)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.offer(a))

  /**
   * @see [[ZQueue.offerAll]]
   */
  override def offerAll(as: Iterable[A])(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.offerAll(as))

  /**
   * @see [[ZQueue.shutdown]]
   */
  override def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit] =
    toEffect(underlying.shutdown)

  /**
   * @see [[ZQueue.size]]
   */
  override def size(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Int] = toEffect(underlying.size)

  /**
   * @see [[ZQueue.take]]
   */
  override def take(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[A] = toEffect(underlying.take)

  /**
   * @see [[ZQueue.takeAll]]
   */
  override def takeAll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]] =
    toEffect(underlying.takeAll.map(_.toList))

  override def takeN(n: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]] =
    toEffect(underlying.takeN(n).map(_.toList))

  /**
   * @see [[ZQueue.takeUpTo]]
   */
  override def takeUpTo(max: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]] =
    toEffect(underlying.takeUpTo(max).map(_.toList))

  /**
   * @see [[ZQueue.poll]]
   */
  override def poll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Option[A]] =
    toEffect(underlying.poll)

}

object Queue {

  /**
   * @see ZioQueue.bounded
   */
  final def bounded[F[+_], A](
    capacity: Int
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(ZioQueue.bounded[A](capacity))

  /**
   * @see ZioQueue.dropping
   */
  final def dropping[F[+_], A](
    capacity: Int
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(ZioQueue.dropping[A](capacity))

  /**
   * @see ZioQueue.sliding
   */
  final def sliding[F[+_], A](
    capacity: Int
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(ZioQueue.sliding[A](capacity))

  /**
   * @see ZioQueue.unbounded
   */
  final def unbounded[F[+_], A](implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(ZioQueue.unbounded[A])

  private final def create[F[+_], A](
    in: UIO[ZioQueue[A]]
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    toEffect(in.map(new Queue[F, A](_)))

}
