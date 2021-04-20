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

import zio.stm.{ TSemaphore => ZTSemaphore }

/**
 * See [[zio.stm.TSemaphore]]
 */
final class TSemaphore[F[+_]] private (underlying: ZTSemaphore) {

  /**
   * See [[zio.stm.TSemaphore#acquire]]
   */
  def acquire: STM[F, Unit] =
    acquireN(1L)

  /**
   * See [[zio.stm.TSemaphore#acquireN]]
   */
  def acquireN(n: Long): STM[F, Unit] =
    new STM(underlying.acquireN(n))

  /**
   * See [[zio.stm.TSemaphore#available]]
   */
  def available: STM[F, Long] =
    new STM(underlying.available)

  /**
   * Switch from effect F to effect G.
   */
  def mapK[G[+_]]: TSemaphore[G] =
    new TSemaphore(underlying)

  /**
   * See [[zio.stm.TSemaphore#release]]
   */
  def release: STM[F, Unit] =
    releaseN(1L)

  /**
   * See [[zio.stm.TSemaphore#releaseN]]
   */
  def releaseN(n: Long): STM[F, Unit] =
    new STM(underlying.releaseN(n))

  /**
   * See [[zio.stm.TSemaphore#withPermit]]
   */
  def withPermit[B](stm: STM[F, B]): STM[F, B] =
    new STM(underlying.withPermit(stm.underlying))
}

object TSemaphore {
  final def make[F[+_]](n: Long): STM[F, TSemaphore[F]] =
    new STM(ZTSemaphore.make(n).map(new TSemaphore(_)))
}
