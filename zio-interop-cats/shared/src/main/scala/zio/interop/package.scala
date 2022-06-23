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

import cats.effect.kernel.{ Async, Outcome, Poll, Resource }
import cats.effect.std.Dispatcher
import cats.syntax.all.*

import java.util.concurrent.atomic.AtomicBoolean

package object interop {

  type CBlocking        = interop.PlatformSpecific.CBlocking
  type CBlockingService = interop.PlatformSpecific.CBlockingService

  type Queue[F[+_], A] = CQueue[F, A, A]
  val Queue: CQueue.type = CQueue

  /** A queue that can only be dequeued. */
  type Dequeue[F[+_], +A] = CQueue[F, Nothing, A]

  /** A queue that can only be enqueued. */
  type Enqueue[F[+_], -A] = CQueue[F, A, Nothing]

  type Hub[F[+_], A] = CHub[F, A, A]
  val Hub: CHub.type = CHub

  @inline def fromEffect[F[_], A](fa: F[A])(implicit F: Dispatcher[F]): Task[A] =
    ZIO
      .effectTotal(F.unsafeToFutureCancelable(fa))
      .flatMap { case (future, cancel) =>
        ZIO.fromFuture(_ => future).onInterrupt(ZIO.fromFuture(_ => cancel()).orDie)
      }

  @inline def toEffect[F[_], R, A](rio: RIO[R, A])(implicit R: Runtime[R], F: Async[F]): F[A] =
    F.defer {
      val interrupted = new AtomicBoolean(true)
      F.async[Exit[Throwable, A]] { cb =>
        val canceler       = R.unsafeRunAsyncCancelable {
          signalOnNoExternalInterrupt {
            rio
          }(ZIO.effectTotal(interrupted.set(false)))
        }(exit => cb(Right(exit)))
        val cancelerEffect = F.delay { val _: Exit[Throwable, A] = canceler(zio.Fiber.Id.None) }
        F.pure(Some(cancelerEffect))
      }.flatMap { exit =>
        toOutcomeThrowableOtherFiber(interrupted.get())(F.pure(_: A), exit) match {
          case Outcome.Succeeded(fa) =>
            fa
          case Outcome.Errored(e)    =>
            F.raiseError(e)
          case Outcome.Canceled()    =>
            F.canceled.flatMap(_ => F.raiseError(exit.asInstanceOf[Exit.Failure[Throwable]].cause.squash))
        }
      }
    }

  implicit class ToEffectSyntax[R, A](private val rio: RIO[R, A]) extends AnyVal {
    @inline def toEffect[F[_]: Async](implicit R: Runtime[R]): F[A] = interop.toEffect(rio)
  }

  @inline private[interop] def toOutcomeCauseOtherFiber[F[_], E, A](
    actuallyInterrupted: Boolean
  )(pure: A => F[A], exit: Exit[E, A]): Outcome[F, Cause[E], A] =
    exit match {
      case Exit.Success(value)                                             =>
        Outcome.Succeeded(pure(value))
      case Exit.Failure(cause) if cause.interrupted && actuallyInterrupted =>
        Outcome.Canceled()
      case Exit.Failure(cause)                                             =>
        Outcome.Errored(cause)
    }

  @inline private[interop] def toOutcomeThrowableOtherFiber[F[_], A](
    actuallyInterrupted: Boolean
  )(pure: A => F[A], exit: Exit[Throwable, A]): Outcome[F, Throwable, A] =
    exit match {
      case Exit.Success(value)                                             =>
        Outcome.Succeeded(pure(value))
      case Exit.Failure(cause) if cause.interrupted && actuallyInterrupted =>
        Outcome.Canceled()
      case Exit.Failure(cause)                                             =>
        cause.failureOrCause match {
          case Left(error)  =>
            Outcome.Errored(error)
          case Right(cause) =>
            val compositeError = dieCauseToThrowable(cause)
            Outcome.Errored(compositeError)
        }
    }

  @inline private[interop] def toOutcomeCauseThisFiber[R, E, A](
    exit: Exit[E, A]
  ): UIO[Outcome[ZIO[R, E, _], Cause[E], A]] =
    exit match {
      case Exit.Success(value) =>
        ZIO.succeedNow(Outcome.Succeeded(ZIO.succeedNow(value)))
      case Exit.Failure(cause) =>
        if (cause.interrupted)
          ZIO.descriptorWith { descriptor =>
            ZIO.succeedNow(
              if (descriptor.interrupters.nonEmpty)
                Outcome.Canceled()
              else
                Outcome.Errored(cause)
            )
          }
        else ZIO.succeedNow(Outcome.Errored(cause))
    }

  private[interop] def toOutcomeThrowableThisFiber[R, A](
    exit: Exit[Throwable, A]
  ): UIO[Outcome[ZIO[R, Throwable, _], Throwable, A]] =
    exit match {
      case Exit.Success(value) =>
        ZIO.succeedNow(Outcome.Succeeded(ZIO.succeedNow(value)))
      case Exit.Failure(cause) =>
        def outcomeErrored: Outcome[ZIO[R, Throwable, _], Throwable, A] =
          cause.failureOrCause match {
            case Left(error)  =>
              Outcome.Errored(error)
            case Right(cause) =>
              val compositeError = dieCauseToThrowable(cause)
              Outcome.Errored(compositeError)
          }

        if (cause.interrupted)
          ZIO.descriptorWith { descriptor =>
            ZIO.succeedNow(
              if (descriptor.interrupters.nonEmpty)
                Outcome.Canceled()
              else
                outcomeErrored
            )
          }
        else ZIO.succeedNow(outcomeErrored)
    }

  private[interop] def toExitCaseThisFiber(exit: Exit[Any, Any]): UIO[Resource.ExitCase] =
    exit match {
      case Exit.Success(_)     =>
        ZIO.succeedNow(Resource.ExitCase.Succeeded)
      case Exit.Failure(cause) =>
        def exitCaseErrored: Resource.ExitCase.Errored =
          cause.failureOrCause match {
            case Left(error: Throwable) =>
              Resource.ExitCase.Errored(error)
            case Left(_)                =>
              Resource.ExitCase.Errored(FiberFailure(cause))
            case Right(cause)           =>
              val compositeError = dieCauseToThrowable(cause)
              Resource.ExitCase.Errored(compositeError)
          }

        if (cause.interrupted)
          ZIO.descriptorWith { descriptor =>
            ZIO.succeedNow(
              if (descriptor.interrupters.nonEmpty)
                Resource.ExitCase.Canceled
              else
                exitCaseErrored
            )
          }
        else
          ZIO.succeedNow(exitCaseErrored)
    }

  @inline private[interop] def toExit(exitCase: Resource.ExitCase): Exit[Throwable, Unit] =
    exitCase match {
      case Resource.ExitCase.Succeeded      => Exit.unit
      case Resource.ExitCase.Canceled       => Exit.interrupt(Fiber.Id.None)
      case Resource.ExitCase.Errored(error) => Exit.fail(error)
    }

  private[interop] def toPoll[R, E](restore: ZIO.InterruptStatusRestore): Poll[ZIO[R, E, _]] = new Poll[ZIO[R, E, _]] {
    override def apply[T](fa: ZIO[R, E, T]): ZIO[R, E, T] = restore(fa)
  }

  @inline private def dieCauseToThrowable(cause: Cause[Nothing]): Throwable =
    cause.defects match {
      case one :: Nil => one
      case _          => FiberFailure(cause)
    }

}
