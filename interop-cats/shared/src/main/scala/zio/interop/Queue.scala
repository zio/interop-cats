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

import cats.effect.kernel.Async
import zio.{ Runtime, UIO, ZEnv, Queue => ZioQueue }

object Queue {

  /**
   * @see ZioQueue.bounded
   */
  final def bounded[F[+_]: Async, A](capacity: Int)(implicit R: Runtime[ZEnv]): F[Queue[F, A]] =
    create(ZioQueue.bounded[A](capacity))

  /**
   * @see ZioQueue.dropping
   */
  final def dropping[F[+_]: Async, A](capacity: Int)(implicit R: Runtime[ZEnv]): F[Queue[F, A]] =
    create(ZioQueue.dropping[A](capacity))

  /**
   * @see ZioQueue.sliding
   */
  final def sliding[F[+_]: Async, A](capacity: Int)(implicit R: Runtime[ZEnv]): F[Queue[F, A]] =
    create(ZioQueue.sliding[A](capacity))

  /**
   * @see ZioQueue.unbounded
   */
  final def unbounded[F[+_]: Async, A](implicit R: Runtime[ZEnv]): F[Queue[F, A]] =
    create(ZioQueue.unbounded[A])

  private final def create[F[+_]: Async, A](in: UIO[ZioQueue[A]])(implicit R: Runtime[ZEnv]): F[Queue[F, A]] =
    toEffect[F, ZEnv, Queue[F, A]](in.map(new CQueue[F, A, A](_)))
}
