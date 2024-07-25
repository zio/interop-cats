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

  @inline def fromEffect[F[_], A](fa: F[A])(implicit F: Dispatcher[F]): Task[A] =
    ZIO
      .succeed(F.unsafeToFutureCancelable(fa))
      .flatMap { case (future, cancel) =>
        ZIO.fromFuture(_ => future).onInterrupt(ZIO.fromFuture(_ => cancel()).orDie)
      }

  @inline def toEffect[F[_], R, A](rio: RIO[R, A])(implicit R: Runtime[R], F: Async[F], trace: Trace): F[A] =
    F.defer {
      val interrupted = new AtomicBoolean(true)
      F.asyncCheckAttempt[Exit[Throwable, A]] { cb =>
        F.delay {
          implicit val unsafe: Unsafe = Unsafe.unsafe

          val out = R.unsafe.runOrFork {
            signalOnNoExternalInterrupt {
              rio
            }(ZIO.succeed(interrupted.set(false)))
          }
          out match {
            case Left(fiber) =>
              val completeCb = (exit: Exit[Throwable, A]) => cb(Right(exit))
              fiber.unsafe.addObserver(completeCb)
              Left(Some(F.async[Unit] { cb =>
                F.delay {
                  val interruptCb = (_: Exit[Throwable, A]) => cb(Right(()))
                  fiber.unsafe.addObserver(interruptCb)
                  fiber.unsafe.removeObserver(completeCb)
                  fiber.tellInterrupt(Cause.interrupt(fiber.id))
                  // Allow the interruption to be interrupted
                  Some(F.delay {
                    fiber.unsafe.removeObserver(interruptCb)
                    interruptCb(null)
                  })
                }
              }))
            case Right(v)    => Right(v) // No need to invoke the callback, sync resumption will take place
          }
        }
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
    @inline def toEffect[F[_]: Async](implicit R: Runtime[R], trace: Trace): F[A] = interop.toEffect(rio)
  }

  @inline private[interop] def toOutcomeCauseOtherFiber[F[_], E, A](
    actuallyInterrupted: Boolean
  )(pure: A => F[A], exit: Exit[E, A]): Outcome[F, Cause[E], A] =
    toOutcomeOtherFiber0(actuallyInterrupted)(pure, exit)((_, c) => c, identity)

  @inline private[interop] def toOutcomeThrowableOtherFiber[F[_], A](
    actuallyInterrupted: Boolean
  )(pure: A => F[A], exit: Exit[Throwable, A]): Outcome[F, Throwable, A] =
    toOutcomeOtherFiber0(actuallyInterrupted)(pure, exit)((e, _) => e, dieCauseToThrowable)

  @inline private[interop] def toOutcomeOtherFiber0[F[_], E, E1, A](
    actuallyInterrupted: Boolean
  )(pure: A => F[A], exit: Exit[E, A])(
    convertFail: (E, Cause[E]) => E1,
    convertDie: Cause[Nothing] => E1
  ): Outcome[F, E1, A] =
    exit match {
      case Exit.Success(value) =>
        Outcome.Succeeded(pure(value))
      case Exit.Failure(cause) =>
        // ZIO 2, unlike ZIO 1, _does not_ guarantee that the presence of a typed failure
        // means we're NOT interrupting, so we have to check for interruption to matter what
        if (
          (cause.isInterrupted || {
            // deem empty cause to be interruption as well, due to occasional invalid ZIO states
            // in `ZIO.fail().uninterruptible` caused by this line https://github.com/zio/zio/blob/22921ee5ac0d2e03531f8b37dfc0d5793a467af8/core/shared/src/main/scala/zio/internal/FiberContext.scala#L415=
            // NOTE: this line is for ZIO 1, it may not apply for ZIO 2, someone needs to debunk
            // whether this is required
            cause.isEmpty
          }) && actuallyInterrupted
        ) {
          Outcome.Canceled()
        } else {
          cause.failureOrCause match {
            case Left(error)  =>
              Outcome.Errored(convertFail(error, cause))
            case Right(cause) =>
              Outcome.Errored(convertDie(cause))
          }
        }
    }

  @inline private[interop] def toOutcomeCauseThisFiber[R, E, A](
    exit: Exit[E, A]
  ): UIO[Outcome[ZIO[R, E, _], Cause[E], A]] =
    toOutcomeThisFiber0(exit)((_, c) => c, identity)

  @inline private[interop] def toOutcomeThrowableThisFiber[R, A](
    exit: Exit[Throwable, A]
  ): UIO[Outcome[ZIO[R, Throwable, _], Throwable, A]] =
    toOutcomeThisFiber0(exit)((e, _) => e, dieCauseToThrowable)

  @inline private def toOutcomeThisFiber0[R, E, E1, A](exit: Exit[E, A])(
    convertFail: (E, Cause[E]) => E1,
    convertDie: Cause[Nothing] => E1
  ): UIO[Outcome[ZIO[R, E, _], E1, A]] = exit match {
    case Exit.Success(value) =>
      ZIO.succeed(Outcome.Succeeded(ZIO.succeed(value)))
    case Exit.Failure(cause) =>
      lazy val nonCanceledOutcome: UIO[Outcome[ZIO[R, E, _], E1, A]] = cause.failureOrCause match {
        case Left(error)  =>
          ZIO.succeed(Outcome.Errored(convertFail(error, cause)))
        case Right(cause) =>
          ZIO.succeed(Outcome.Errored(convertDie(cause)))
      }
      // ZIO 2, unlike ZIO 1, _does not_ guarantee that the presence of a typed failure
      // means we're NOT interrupting, so we have to check for interruption to matter what
      if (
        cause.isInterrupted || {
          // deem empty cause to be interruption as well, due to occasional invalid ZIO states
          // in `ZIO.fail().uninterruptible` caused by this line https://github.com/zio/zio/blob/22921ee5ac0d2e03531f8b37dfc0d5793a467af8/core/shared/src/main/scala/zio/internal/FiberContext.scala#L415=
          // NOTE: this line is for ZIO 1, it may not apply for ZIO 2, someone needs to debunk
          // whether this is required
          cause.isEmpty
        }
      ) {
        ZIO.descriptorWith { descriptor =>
          if (descriptor.interrupters.nonEmpty)
            ZIO.succeed(Outcome.Canceled())
          else {
            nonCanceledOutcome
          }
        }
      } else {
        nonCanceledOutcome
      }
  }

  private[interop] def toExitCaseThisFiber(exit: Exit[Any, Any])(implicit trace: Trace): UIO[Resource.ExitCase] =
    exit match {
      case Exit.Success(_)     =>
        ZIO.succeed(Resource.ExitCase.Succeeded)
      case Exit.Failure(cause) =>
        lazy val nonCanceledOutcome: UIO[Resource.ExitCase] = cause.failureOrCause match {
          case Left(error: Throwable) =>
            ZIO.succeed(Resource.ExitCase.Errored(error))
          case Left(_)                =>
            ZIO.succeed(Resource.ExitCase.Errored(FiberFailure(cause)))
          case Right(cause)           =>
            ZIO.succeed(Resource.ExitCase.Errored(dieCauseToThrowable(cause)))
        }
        // ZIO 2, unlike ZIO 1, _does not_ guarantee that the presence of a typed failure
        // means we're NOT interrupting, so we have to check for interruption to matter what
        if (
          cause.isInterrupted || {
            // deem empty cause to be interruption as well, due to occasional invalid ZIO states
            // in `ZIO.fail().uninterruptible` caused by this line https://github.com/zio/zio/blob/22921ee5ac0d2e03531f8b37dfc0d5793a467af8/core/shared/src/main/scala/zio/internal/FiberContext.scala#L415=
            // NOTE: this line is for ZIO 1, it may not apply for ZIO 2, someone needs to debunk
            // whether this is required
            cause.isEmpty
          }
        ) {
          ZIO.descriptorWith { descriptor =>
            if (descriptor.interrupters.nonEmpty) {
              ZIO.succeed(Resource.ExitCase.Canceled)
            } else
              nonCanceledOutcome
          }
        } else {
          nonCanceledOutcome
        }
    }

  @inline private[interop] def toExit(exitCase: Resource.ExitCase): Exit[Throwable, Unit] =
    exitCase match {
      case Resource.ExitCase.Succeeded      => Exit.unit
      case Resource.ExitCase.Canceled       => Exit.interrupt(FiberId.None)
      case Resource.ExitCase.Errored(error) => Exit.fail(error)
    }

  @inline private[interop] def toPoll[R, E](restore: ZIO.InterruptibilityRestorer): Poll[ZIO[R, E, _]] =
    new Poll[ZIO[R, E, _]] {
      override def apply[T](fa: ZIO[R, E, T]): ZIO[R, E, T] = restore(fa)
    }

  @inline private[interop] def signalOnNoExternalInterrupt[R, E, A](
    f: ZIO[R, E, A]
  )(notInterrupted: UIO[Unit]): ZIO[R, E, A] =
    f.onExit {
      case Exit.Success(_) => ZIO.unit
      case Exit.Failure(_) =>
        // we don't check if cause is interrupted
        // because we can get an invalid state Cause.empty
        // due to this line https://github.com/zio/zio/blob/22921ee5ac0d2e03531f8b37dfc0d5793a467af8/core/shared/src/main/scala/zio/internal/FiberContext.scala#L415=
        // if the last error was an uninterruptible typed error
        ZIO.descriptorWith(d => if (d.interrupters.isEmpty) notInterrupted else ZIO.unit)
    }

  @inline private[interop] def dieCauseToThrowable(cause: Cause[Nothing]): Throwable =
    cause.defects match {
      case one :: Nil => one
      case _          => FiberFailure(cause)
    }

}
