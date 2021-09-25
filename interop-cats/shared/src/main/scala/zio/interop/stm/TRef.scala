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

import cats.effect.kernel.Async
import zio.Runtime
import zio.stm.TRef as ZTRef

/**
 * See [[zio.stm.TRef]]
 */
final class TRef[F[+_], A] private (underlying: ZTRef[A]) {

  /**
   * See [[zio.stm.ZTRef#get]]
   */
  def get: STM[F, A] =
    new STM(underlying.get)

  /**
   * Switch from effect F to effect G.
   */
  def mapK[G[+_]]: TRef[G, A] =
    new TRef(underlying)

  /**
   * See [[zio.stm.ZTRef.UnifiedSyntax#modify]]
   */
  def modify[B](f: A => (B, A)): STM[F, B] =
    new STM(underlying.modify(f))

  /**
   * See [[zio.stm.ZTRef.UnifiedSyntax#modifySome]]
   */
  def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): STM[F, B] =
    new STM(underlying.modifySome(default)(f))

  /**
   * See [[zio.stm.ZTRef#set]]
   */
  def set(newValue: A): STM[F, Unit] =
    new STM(underlying.set(newValue))

  override def toString: String      =
    underlying.toString

  /**
   * See [[zio.stm.ZTRef.UnifiedSyntax#update]]
   */
  def update(f: A => A): STM[F, A] =
    new STM(underlying.updateAndGet(f))

  /**
   * See [[zio.stm.ZTRef.UnifiedSyntax#updateSome]]
   */
  def updateSome(f: PartialFunction[A, A]): STM[F, A] =
    new STM(underlying.updateSomeAndGet(f))
}

object TRef {

  final def make[F[+_], A](a: => A): STM[F, TRef[F, A]]                                   =
    new STM(ZTRef.make(a).map(new TRef(_)))

  final def makeCommit[F[+_]: Async, A](a: => A)(implicit R: Runtime[Any]): F[TRef[F, A]] =
    STM.atomically(make(a))
}
