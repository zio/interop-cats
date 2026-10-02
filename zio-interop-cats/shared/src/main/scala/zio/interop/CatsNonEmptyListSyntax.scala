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

import cats.data.NonEmptyList
import zio.{ NonEmptyChunk, Trace, ZIO }

trait CatsNonEmptyListSyntax {
  import scala.language.implicitConversions

  implicit final def zioNonEmptyListSyntax(self: ZIO.type): ZIONonEmptyListSyntax =
    new ZIONonEmptyListSyntax(self)

  implicit final def nonEmptyListSyntax[A](as: NonEmptyList[A]): NonEmptyListSyntax[A] =
    new NonEmptyListSyntax(as)

  implicit final def nonEmptyListOfZIOSyntax[R, E, A](
    as: NonEmptyList[ZIO[R, E, A]]
  ): NonEmptyListOfZIOSyntax[R, E, A] =
    new NonEmptyListOfZIOSyntax(as)
}

/**
 * `ZIO.foreach` and friends for [[cats.data.NonEmptyList]], which return a `NonEmptyList`.
 *
 * They can't be overloads of `ZIO.foreach` and friends, because extension methods are not
 * considered for names that `ZIO` already defines. The same operations are available without the
 * `Nel` suffix as extension methods on `NonEmptyList` itself (`nel.foreach`, `nel.foreachPar`).
 */
final class ZIONonEmptyListSyntax(private val self: ZIO.type) extends AnyVal {

  /**
   * Like `ZIO.foreach`, for a `NonEmptyList`.
   */
  def foreachNel[R, E, A, B](as: NonEmptyList[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, NonEmptyList[B]] =
    ZIO.foreach(toNonEmptyChunk(as))(f).map(toNonEmptyList)

  /**
   * Like `ZIO.foreachPar`, for a `NonEmptyList`.
   */
  def foreachParNel[R, E, A, B](as: NonEmptyList[A])(f: A => ZIO[R, E, B])(implicit
    trace: Trace
  ): ZIO[R, E, NonEmptyList[B]] =
    ZIO.foreachPar(toNonEmptyChunk(as))(f).map(toNonEmptyList)

  /**
   * Like `ZIO.collectAll`, for a `NonEmptyList`.
   */
  def collectAllNel[R, E, A](as: NonEmptyList[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, NonEmptyList[A]] =
    ZIO.collectAll(toNonEmptyChunk(as)).map(toNonEmptyList)

  /**
   * Like `ZIO.collectAllPar`, for a `NonEmptyList`.
   */
  def collectAllParNel[R, E, A](as: NonEmptyList[ZIO[R, E, A]])(implicit trace: Trace): ZIO[R, E, NonEmptyList[A]] =
    ZIO.collectAllPar(toNonEmptyChunk(as)).map(toNonEmptyList)

  private def toNonEmptyChunk[A](as: NonEmptyList[A]): NonEmptyChunk[A] =
    NonEmptyChunk.fromIterable(as.head, as.tail)

  private def toNonEmptyList[A](as: NonEmptyChunk[A]): NonEmptyList[A] =
    NonEmptyList(as.head, as.tail.toList)
}

/**
 * `foreach` / `foreachPar` for a [[cats.data.NonEmptyList]]. The `Nel` suffix is unnecessary here:
 * the receiver is already a `NonEmptyList`, and `NonEmptyList` does not define these methods.
 */
final class NonEmptyListSyntax[A](private val as: NonEmptyList[A]) extends AnyVal {

  /**
   * Like `ZIO.foreachNel`. Runs `f` sequentially and stops at the first failure.
   */
  def foreach[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, NonEmptyList[B]] =
    new ZIONonEmptyListSyntax(ZIO).foreachNel(as)(f)

  /**
   * Like `ZIO.foreachParNel`. Runs `f` in parallel and preserves the input order.
   */
  def foreachPar[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, NonEmptyList[B]] =
    new ZIONonEmptyListSyntax(ZIO).foreachParNel(as)(f)
}

/**
 * `collectAll` / `collectAllPar` for a [[cats.data.NonEmptyList]] of effects.
 */
final class NonEmptyListOfZIOSyntax[R, E, A](private val as: NonEmptyList[ZIO[R, E, A]]) extends AnyVal {

  /**
   * Like `ZIO.collectAllNel`.
   */
  def collectAll(implicit trace: Trace): ZIO[R, E, NonEmptyList[A]] =
    new ZIONonEmptyListSyntax(ZIO).collectAllNel(as)

  /**
   * Like `ZIO.collectAllParNel`.
   */
  def collectAllPar(implicit trace: Trace): ZIO[R, E, NonEmptyList[A]] =
    new ZIONonEmptyListSyntax(ZIO).collectAllParNel(as)
}
