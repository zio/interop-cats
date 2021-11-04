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

package zio.interop.stm

import zio.stm.TQueue as ZTQueue

/**
 * See [[zio.stm.TQueue]]
 */
final class TQueue[F[+_], A] private (underlying: ZTQueue[A]) {

  /**
   * Switch from effect F to effect G.
   */
  def mapK[G[+_]]: TQueue[G, A] =
    new TQueue(underlying)

  /**
   * See [[zio.stm.ZTQueue#offer]]
   */
  def offer(a: A): STM[F, Boolean] =
    new STM(underlying.offer(a))

  /**
   * See [[zio.stm.ZTQueue#offerAll]]
   */
  def offerAll(as: List[A]): STM[F, Boolean] =
    new STM(underlying.offerAll(as))

  /**
   * See [[zio.stm.ZTQueue#poll]]
   */
  def poll: STM[F, Option[A]] =
    new STM(underlying.poll)

  /**
   * See [[zio.stm.ZTQueue#size]]
   */
  def size: STM[F, Int] =
    new STM(underlying.size)

  /**
   * See [[zio.stm.ZTQueue#take]]
   */
  def take: STM[F, A] =
    new STM(underlying.take)

  /**
   * See [[zio.stm.ZTQueue#takeAll]]
   */
  def takeAll: STM[F, List[A]] =
    new STM(underlying.takeAll.map(_.toList))

  /**
   * See [[zio.stm.ZTQueue#takeUpTo]]
   */
  def takeUpTo(max: Int): STM[F, List[A]] =
    new STM(underlying.takeUpTo(max).map(_.toList))
}

object TQueue {
  final def make[F[+_], A](capacity: Int): STM[F, TQueue[F, A]] =
    new STM(ZTQueue.bounded[A](capacity).map(new TQueue(_)))
}
