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

import cats.effect.kernel.Poll
import zio.{ FiberId, FiberRef, Trace, Unsafe, ZIO }

private[interop] final case class CatsMask(fiber: FiberId, depth: Int)

private[interop] object CatsMask {

  private val current: FiberRef[CatsMask] =
    FiberRef.unsafe.make(CatsMask(FiberId.None, 0))(Unsafe.unsafe)

  def uncancelable[R, E, R1, E1, A](body: Poll[ZIO[R1, E1, _]] => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.uninterruptibleMask { restore =>
      current.getWith { outer =>
        val inner = outer.copy(depth = outer.depth + 1)
        current.locally(inner) {
          body(new Poll[ZIO[R1, E1, _]] {
            override def apply[T](fa: ZIO[R1, E1, T]): ZIO[R1, E1, T] =
              current.getWith(mask => if (mask == inner) current.locally(outer)(restore(fa)) else fa)
          })
        }
      }
    }

  def finalizer[R, E, A](canceled: Boolean)(fin: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    current.getWith(mask => current.locally(mask.copy(depth = if (canceled) 1 else mask.depth + 1))(fin))

  def unmasked[R, E, A](fa: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.fiberIdWith(fiber => current.locally(CatsMask(fiber, 0))(fa.interruptible))
}
