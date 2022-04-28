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
import zio.{ Runtime, Trace, UIO, ZEnv }

/**
 * @see [[zio.Dequeue]]
 */
sealed trait Dequeue[F[+_], +A] {

  /**
   * @see [[zio.Dequeue.awaitShutdown]]
   */
  def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[zio.Dequeue.capacity]]
   */
  def capacity: Int

  /**
   * @see [[zio.Dequeue.isEmpty]]
   */
  def isEmpty(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Dequeue.isFull]]
   */
  def isFull(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Dequeue.isShutdown]]
   */
  def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Dequeue.poll]]
   */
  def poll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Option[A]]

  /**
   * @see [[zio.Dequeue.shutdown]]
   */
  def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[zio.Dequeue.size]]
   */
  def size(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Int]

  /**
   * @see [[zio.Dequeue.take]]
   */
  def take(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[A]

  /**
   * @see [[zio.Dequeue.takeAll]]
   */
  def takeAll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]]

  /**
   * @see [[zio.Dequeue.takeN]]
   */
  def takeN(n: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]]

  /**
   * @see [[zio.Dequeue.takeUpTo]]
   */
  def takeUpTo(max: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]]

}

/**
 * @see [[zio.Enqueue]]
 */
sealed trait Enqueue[F[+_], -A] {

  /**
   * @see [[zio.Enqueue.awaitShutdown]]
   */
  def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[zio.Enqueue.capacity]]
   */
  def capacity: Int

  /**
   * @see [[zio.Enqueue.isEmpty]]
   */
  def isEmpty(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Enqueue.isFull]]
   */
  def isFull(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Enqueue.isShutdown]]
   */
  def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Enqueue.offer]]
   */
  def offer(a: A)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Enqueue.offerAll]]
   */
  def offerAll(as: Iterable[A])(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean]

  /**
   * @see [[zio.Enqueue.shutdown]]
   */
  def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit]

  /**
   * @see [[zio.Enqueue.size]]
   */
  def size(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Int]

}

/**
 * @see [[zio.Queue]]
 */
final class Queue[F[+_], A] private[interop] (
  private val underlying: zio.Queue[A]
) extends Dequeue[F, A]
    with Enqueue[F, A] {

  /**
   * @see [[zio.Queue.awaitShutdown]]
   */
  override def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit] =
    toEffect(underlying.awaitShutdown)

  /**
   * @see [[zio.Queue.capacity]]
   */
  override def capacity: Int = underlying.capacity

  /**
   * @see [[zio.Queue.isEmpty]]
   */
  override def isEmpty(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.isEmpty)

  /**
   * @see [[zio.Queue.isFull]]
   */
  override def isFull(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.isFull)

  /**
   * @see [[zio.Queue.isShutdown]]
   */
  override def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.isShutdown)

  /**
   * @see [[zio.Queue.offer]]
   */
  override def offer(a: A)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.offer(a))

  /**
   * @see [[zio.Queue.offerAll]]
   */
  override def offerAll(as: Iterable[A])(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Boolean] =
    toEffect(underlying.offerAll(as))

  /**
   * @see [[zio.Queue.shutdown]]
   */
  override def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Unit] =
    toEffect(underlying.shutdown)

  /**
   * @see [[zio.Queue.size]]
   */
  override def size(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Int] = toEffect(underlying.size)

  /**
   * @see [[zio.Queue.take]]
   */
  override def take(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[A] = toEffect(underlying.take)

  /**
   * @see [[zio.Queue.takeAll]]
   */
  override def takeAll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]] =
    toEffect(underlying.takeAll.map(_.toList))

  /**
   * @see [[zio.Queue.takeN]]
   */
  override def takeN(n: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]] =
    toEffect(underlying.takeN(n).map(_.toList))

  /**
   * @see [[zio.Queue.takeUpTo]]
   */
  override def takeUpTo(max: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[List[A]] =
    toEffect(underlying.takeUpTo(max).map(_.toList))

  /**
   * @see [[zio.Queue.poll]]
   */
  override def poll(implicit R: Runtime[Any], F: LiftIO[F], trace: Trace): F[Option[A]] =
    toEffect(underlying.poll)

}

object Queue {

  /**
   * @see zio.Queue.bounded
   */
  final def bounded[F[+_], A](
    capacity: Int
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(zio.Queue.bounded[A](capacity))

  /**
   * @see zio.Queue.dropping
   */
  final def dropping[F[+_], A](
    capacity: Int
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(zio.Queue.dropping[A](capacity))

  /**
   * @see zio.Queue.sliding
   */
  final def sliding[F[+_], A](
    capacity: Int
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(zio.Queue.sliding[A](capacity))

  /**
   * @see zio.Queue.unbounded
   */
  final def unbounded[F[+_], A](implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    create(zio.Queue.unbounded[A])

  private final def create[F[+_], A](
    in: UIO[zio.Queue[A]]
  )(implicit R: Runtime[ZEnv], F: LiftIO[F], trace: Trace): F[Queue[F, A]] =
    toEffect(in.map(new Queue[F, A](_)))

}
