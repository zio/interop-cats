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

import cats.arrow.FunctionK
import cats.effect.{ Async, Effect, ExitCase, LiftIO, Resource }
import cats.~>
import zio.interop.CatsResourceObjectSyntax.FromScopedZIOPartiallyApplied
import zio.interop.CatsResourceObjectSyntax.FromScopedPartiallyApplied

trait CatsScopedSyntax {
  import scala.language.implicitConversions

  implicit final def catsIOResourceSyntax[F[_], A](resource: Resource[F, A]): CatsIOResourceSyntax[F, A] =
    new CatsIOResourceSyntax(resource)

  implicit final def zioResourceSyntax[R, E <: Throwable, A](r: Resource[ZIO[R, E, *], A]): ZIOResourceSyntax[R, E, A] =
    new ZIOResourceSyntax(r)

  implicit final def catsIOResourceObjectSyntax(unused: Resource.type): CatsResourceObjectSyntax =
    new CatsResourceObjectSyntax(unused)

}

final class CatsIOResourceSyntax[F[_], A](private val resource: Resource[F, A]) extends AnyVal {

  /**
   * Convert a cats Resource into a ZIO with a Scope.
   * Beware that unhandled error during release of the resource will result in the fiber dying.
   */
  def toScoped[R](implicit L: LiftIO[ZIO[R, Throwable, *]], F: Effect[F]): ZIO[R with Scope, Throwable, A] = {
    import catz.core._

    new ZIOResourceSyntax[R, Throwable, A](
      resource.mapK(
        new ~>[F, ZIO[R, Throwable, *]] {
          def apply[A](fa: F[A]): ZIO[R, Throwable, A] =
            L liftIO F.toIO(fa)
        }
      )
    ).toScopedZIO
  }
}

final class CatsResourceObjectSyntax(private val resource: Resource.type) extends AnyVal {

  def scopedZIO[R]: CatsResourceObjectSyntax.FromScopedZIOPartiallyApplied[R] =
    new FromScopedZIOPartiallyApplied[R]

  def scoped[F[_], R]: CatsResourceObjectSyntax.FromScopedPartiallyApplied[F, R] =
    new FromScopedPartiallyApplied[F, R]

}

object CatsResourceObjectSyntax {

  final class FromScopedZIOPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](scoped: ZIO[R with Scope, E, A])(implicit trace: Trace): Resource[ZIO[R, E, *], A] =
      Resource
        .applyCase[ZIO[R, E, *], Scope.Closeable](
          Scope.makeWith(ExecutionStrategy.Sequential).map { closeable =>
            (closeable, (exitCase: ExitCase[Throwable]) => closeable.close(exitCaseToExit(exitCase)))
          }
        )
        .flatMap(
          closeable =>
            Resource.suspend(
              closeable.extend[R] {
                scoped.map { a =>
                  Resource.applyCase(ZIO.succeedNow((a, (_: ExitCase[Throwable]) => ZIO.unit)))
                }
              }
            )
        )
  }

  final class FromScopedPartiallyApplied[F[_], R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](
      scoped: ZIO[R with Scope, E, A]
    )(implicit F: Async[F], ev: Effect[ZIO[R, E, *]], trace: Trace): Resource[F, A] =
      new CatsResourceObjectSyntax(Resource)
        .scopedZIO[R](scoped)
        .mapK(new FunctionK[ZIO[R, E, *], F] {
          def apply[A](fa: ZIO[R, E, A]): F[A] =
            F liftIO ev.toIO(fa)
        })
  }

}
