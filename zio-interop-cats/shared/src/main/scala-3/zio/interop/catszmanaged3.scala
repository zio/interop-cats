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

package zio
package interop

import cats.arrow.{ ArrowChoice, FunctionK }
import cats.effect.Resource.{ Allocate, Bind, Suspend }
import cats.effect.{ Async, Effect, ExitCase, LiftIO, Resource, Sync, IO => CIO }
import cats.{ ~>, Bifunctor, Monad, MonadError, Monoid, Semigroup, SemigroupK }
import zio.ZManaged.ReleaseMap

final class ZIOResourceSyntax[R, E <: Throwable, A](private val resource: Resource[ZIO[R, E, *], A]) extends AnyVal {

  /**
   * Convert a cats Resource into a ZManaged.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toManagedZIO(implicit trace: ZTraceElement): ZManaged[R, E, A] = {
    def go[A1](res: Resource[ZIO[R, E, *], A1]): ZManaged[R, E, A1] =
      res match {
        case Allocate(resource) =>
          ZManaged.fromReservationZIO(resource.map {
            case (a, r) => Reservation(ZIO.succeedNow(a), e => r(exitToExitCase(e)).orDie)
          })
        case Bind(source, fs) =>
          go(source).flatMap(s => go(fs(s)))
        case Suspend(resource) =>
          ZManaged.unwrap(resource.map(go))
      }

    go(resource)
  }
}
