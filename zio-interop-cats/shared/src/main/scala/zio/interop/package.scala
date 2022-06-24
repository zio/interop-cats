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

import cats.effect.kernel.{ Async, Outcome, Resource }
import cats.effect.std.Dispatcher
import cats.syntax.all.*

import scala.concurrent.Future

package object interop {

  @inline private[interop] def toOutcome[R, E, A](
    exit: Exit[E, A]
  )(implicit trace: Trace): Outcome[ZIO[R, E, _], E, A] =
    exit match {
      case Exit.Success(value)                        =>
        Outcome.Succeeded(ZIO.succeed(value))
      case Exit.Failure(cause) if cause.isInterrupted =>
        Outcome.Canceled()
      case Exit.Failure(cause)                        =>
        cause.failureOrCause match {
          case Left(error)  => Outcome.Errored(error)
          case Right(cause) => Outcome.Succeeded(ZIO.failCause(cause))
        }
    }

  @inline private[interop] def toExit(exitCase: Resource.ExitCase): Exit[Throwable, Unit] =
    exitCase match {
      case Resource.ExitCase.Succeeded      => Exit.unit
      case Resource.ExitCase.Canceled       => Exit.interrupt(FiberId.None)
      case Resource.ExitCase.Errored(error) => Exit.fail(error)
    }

  @inline private[interop] def toExitCase(exit: Exit[Any, Any]): Resource.ExitCase =
    exit match {
      case Exit.Success(_)                            =>
        Resource.ExitCase.Succeeded
      case Exit.Failure(cause) if cause.isInterrupted =>
        Resource.ExitCase.Canceled
      case Exit.Failure(cause)                        =>
        cause.failureOrCause match {
          case Left(error: Throwable) => Resource.ExitCase.Errored(error)
          case _                      => Resource.ExitCase.Errored(FiberFailure(cause))
        }
    }

  @inline def fromEffect[F[_], A](fa: F[A])(implicit F: Dispatcher[F], trace: Trace): Task[A] =
    ZIO
      .succeed(F.unsafeToFutureCancelable(fa))
      .flatMap { case (future, cancel) =>
        ZIO.fromFuture(_ => future).onInterrupt(ZIO.fromFuture(_ => cancel()).orDie).interruptible
      }
      .uninterruptible

  @inline def toEffect[F[_], R, A](
    rio: RIO[R, A]
  )(implicit R: Runtime[R], F: Async[F], trace: Trace): F[A] =
    F.uncancelable { poll =>
      Unsafe.unsafeCompat { implicit u =>
        F.delay(R.unsafe.runToFuture(rio)).flatMap { future =>
          poll(F.onCancel(F.fromFuture(F.pure[Future[A]](future)), F.fromFuture(F.delay(future.cancel())).void))
        }
      }
    }

  implicit class ToEffectSyntax[R, A](private val rio: RIO[R, A]) extends AnyVal {
    @inline def toEffect[F[_]: Async](implicit R: Runtime[R], trace: Trace): F[A] = interop.toEffect(rio)
  }
}
