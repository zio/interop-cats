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

import cats.effect.ExitCase

package object interop {
  type ParIO[-R, +E, +A] = Par.T[R, E, A]

  @inline private[interop] final def exitToExitCase(exit: Exit[Any, Any]): ExitCase[Throwable] = exit match {
    case Exit.Success(_)                          => ExitCase.Completed
    case Exit.Failure(cause) if cause.interrupted => ExitCase.Canceled
    case Exit.Failure(cause) =>
      cause.failureOrCause match {
        case Left(t: Throwable) => ExitCase.Error(t)
        case _                  => ExitCase.Error(FiberFailure(cause))
      }
  }

  @inline private[interop] final def exitCaseToExit[E](exitCase: ExitCase[E]): Exit[E, Unit] = exitCase match {
    case ExitCase.Completed => Exit.unit
    case ExitCase.Error(e)  => Exit.fail(e)
    case ExitCase.Canceled  => Exit.interrupt
  }
}
