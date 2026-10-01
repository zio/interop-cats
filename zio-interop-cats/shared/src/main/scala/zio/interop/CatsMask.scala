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
import zio.{ FiberId, FiberRef, RuntimeFlags, Trace, Unsafe, ZIO }

/**
 * cats-effect masking semantics (`MonadCancel#uncancelable`) on top of ZIO
 * interruptibility.
 *
 * Like cats-effect's `IOFiber`, every fiber tracks the depth of its nested
 * masks, and a `Poll` is bound to the fiber and to the depth of the mask that
 * created it. A `Poll` only restores the interruptibility of the region
 * enclosing its mask when it is applied on that fiber while its mask is the
 * innermost one, otherwise it is a no-op. Consequently `poll(poll(fa))` is
 * `poll(fa)`, polls applied in the wrong order are a no-op, and a `Poll`
 * applied in a different fiber is a no-op.
 */
private[interop] object CatsMask {

  /**
   * Mask depth of the fiber `owner`, only read and written by that fiber.
   *
   * Mutable, so that entering a nested mask or applying a `Poll` doesn't
   * have to update the fiber's `FiberRefs`.
   */
  private final class MaskState(val owner: FiberId) {
    var depth: Int = 0
  }

  private val unmasked: MaskState = new MaskState(FiberId.None)

  /**
   * The `MaskState` of the current fiber, only set while the fiber is inside
   * a mask.
   *
   * A forked fiber inherits its parent's `MaskState`, which it doesn't own,
   * so it starts unmasked. Joining a fiber keeps the joining fiber's
   * `MaskState`.
   */
  private val currentState: FiberRef[MaskState] =
    FiberRef.unsafe.make[MaskState](unmasked, identity, (parent, _) => parent)(Unsafe.unsafe)

  /**
   * @tparam R1 environment of the effects accepted by the `Poll`
   * @tparam E1 error type of the effects accepted by the `Poll`
   */
  def uncancelable[R, E, R1, E1, A](body: Poll[ZIO[R1, E1, _]] => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.uninterruptibleMask { restore =>
      ZIO.withFiberRuntime[R, E, A] { (fiber, _) =>
        val inherited = fiber.getFiberRef(currentState)
        val owned     = inherited.owner eq fiber.id
        val maskDepth = if (owned) inherited.depth + 1 else 1
        val masked    = body(new MaskPoll[R1, E1](restore, fiber.id, maskDepth))
        val state     =
          if (owned) inherited
          else {
            val state = new MaskState(fiber.id)
            fiber.setFiberRef(currentState, state)
            state
          }
        state.depth = maskDepth
        // this continuation runs uninterruptibly, so the depth is restored on every exit
        masked.exitWith { exit =>
          state.depth = maskDepth - 1
          // only the outermost mask, which installed the state, removes it
          if (!owned) fiber.resetFiberRef(currentState)
          exit
        }
      }
    }

  private final class MaskPoll[R, E](
    restore: ZIO.InterruptibilityRestorer,
    fiberId: FiberId.Runtime,
    maskDepth: Int
  ) extends Poll[ZIO[R, E, _]] {
    override def apply[A](fa: ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.withFiberRuntime[R, E, A] { (fiber, status) =>
        val state = fiber.getFiberRef(currentState)
        if ((fiber.id eq fiberId) && (state.owner eq fiberId) && state.depth == maskDepth) {
          if (RuntimeFlags.interruption(status.runtimeFlags)) {
            // made interruptible by ZIO inside the mask: restoring the depth in an interruptible
            // continuation could be skipped by an interruption, so restore it uninterruptibly
            ZIO.uninterruptible(apply(fa))
          } else {
            state.depth = maskDepth - 1
            restore(fa).exitWith { exit =>
              state.depth = maskDepth
              exit
            }
          }
        } else fa
      }
  }
}
