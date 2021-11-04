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

import cats.effect.{ Effect, LiftIO }
import zio.{ Runtime, ZQueue, ZTraceElement }

/**
 * @see [[zio.ZQueue]]
 */
final class CQueue[F[+_], -A, +B] private[interop] (
  private val underlying: ZQueue[Any, Any, Throwable, Throwable, A, B]
) {

  /**
   * @see [[ZQueue.awaitShutdown]]
   */
  def awaitShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Unit] =
    toEffect(underlying.awaitShutdown)

  /**
   * @see [[ZQueue.capacity]]
   */
  def capacity: Int = underlying.capacity

  /**
   * @see [[ZQueue.isShutdown]]
   */
  def isShutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Boolean] =
    toEffect(underlying.isShutdown)

  /**
   * @see [[ZQueue.offer]]
   */
  def offer(a: A)(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Boolean] =
    toEffect(underlying.offer(a))

  /**
   * @see [[ZQueue.offerAll]]
   */
  def offerAll(as: Iterable[A])(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Boolean] =
    toEffect(underlying.offerAll(as))

  /**
   * @see [[ZQueue.shutdown]]
   */
  def shutdown(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Unit] = toEffect(underlying.shutdown)

  /**
   * @see [[ZQueue.size]]
   */
  def size(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Int] = toEffect(underlying.size)

  /**
   * @see [[ZQueue.take]]
   */
  def take(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[B] = toEffect(underlying.take)

  /**
   * @see [[ZQueue.takeAll]]
   */
  def takeAll(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[List[B]] =
    toEffect(underlying.takeAll.map(_.toList))

  /**
   * @see [[ZQueue.takeUpTo]]
   */
  def takeUpTo(max: Int)(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[List[B]] =
    toEffect(underlying.takeUpTo(max).map(_.toList))

  /**
   * @see [[ZQueue.contramap]]
   */
  def contramap[C](f: C => A): CQueue[F, C, B] = new CQueue(underlying.contramap(f))

  /**
   * @see [[ZQueue.contramapZIO]]
   */
  def contramapM[C](f: C => F[A])(implicit R: Runtime[Any], E: Effect[F]): CQueue[F, C, B] =
    new CQueue(underlying.contramapZIO(c => fromEffect(f(c))))

  /**
   * @see [[ZQueue.filterInput]]
   */
  def filterInput[A0 <: A](f: A0 => Boolean): CQueue[F, A0, B] = new CQueue(underlying.filterInput(f))

  /**
   * @see [[ZQueue.filterInputZIO]]
   */
  def filterInputM[A0 <: A](f: A0 => F[Boolean])(implicit R: Runtime[Any], E: Effect[F]): CQueue[F, A0, B] =
    new CQueue(underlying.filterInputZIO((a0: A0) => fromEffect(f(a0))))

  /**
   * @see [[ZQueue.map]]
   */
  def map[C](f: B => C): CQueue[F, A, C] = new CQueue(underlying.map(f))

  /**
   * @see [[ZQueue.mapZIO]]
   */
  def mapM[C](f: B => F[C])(implicit R: Runtime[Any], E: Effect[F]): CQueue[F, A, C] =
    new CQueue(underlying.mapZIO(b => fromEffect(f(b))))

  /**
   * @see [[ZQueue.poll]]
   */
  def poll(implicit R: Runtime[Any], F: LiftIO[F], trace: ZTraceElement): F[Option[B]] = toEffect(underlying.poll)
}
