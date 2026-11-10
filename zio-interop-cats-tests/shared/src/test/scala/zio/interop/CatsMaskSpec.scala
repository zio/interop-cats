package zio.interop

import cats.effect.kernel.{ Async, GenConcurrent, Poll, Resource }
import cats.effect.IO as CIO
import cats.effect.std.NonEmptyHotswap
import cats.syntax.all.*
import zio.interop.catz.*
import zio.test.*
import zio.*

object CatsMaskSpec extends CatsRunnableSpec {

  abstract class Scenario(val name: String, val marks: Int) {
    def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit]
  }

  abstract class AsyncScenario(val name: String, val marks: Int) {
    def run[F[_]](mark: F[Unit])(implicit F: Async[F]): F[Unit]
  }

  val scenarios: List[Scenario] = List(
    new Scenario("outer poll within a nested mask is a no-op", marks = 1)                             {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(_ => outer(F.canceled) *> mark))
    },
    new Scenario("polls applied outermost-first unmask", marks = 0)                                   {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(inner => inner(outer(F.canceled)) *> mark))
    },
    new Scenario("polls applied in the wrong order are a no-op", marks = 1)                           {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(inner => outer(inner(F.canceled)) *> mark))
    },
    new Scenario("poll(poll(fa)) is poll(fa)", marks = 0)                                             {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => poll(poll(F.canceled)) *> mark)
    },
    new Scenario("mask is reinstated after a failed poll", marks = 1)                                 {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable { outer =>
          F.uncancelable(inner => inner(F.raiseError[Unit](error)).handleError(_ => ()) *> outer(F.canceled) *> mark)
        }
    },
    new Scenario("poll of an exited mask unmasks within a new mask of the same depth", marks = 0)     {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => F.pure(poll)).flatMap((poll: Poll[F]) => F.uncancelable(_ => poll(F.canceled) *> mark))
    },
    new Scenario("poll applied within a started fiber is a no-op", marks = 1)                         {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => F.start(F.uncancelable(_ => poll(F.canceled) *> mark)).flatMap(_.join).void)
    },
    new Scenario("a racePair fiber started within nested masks starts unmasked", marks = 0)           {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable { _ =>
          F.uncancelable { inner =>
            F.racePair(inner(F.canceled *> mark), inner(F.canceled *> mark)).flatMap {
              case Left((_, fiber))  => fiber.cancel
              case Right((fiber, _)) => fiber.cancel
            }
          }
        }
    },
    new Scenario("a fiber started within nested masks starts unmasked", marks = 0)                    {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(_ => F.uncancelable(inner => F.start(inner(F.canceled *> mark)).flatMap(_.join).void))
    },
    new Scenario("outer poll within a guarantee finalizer is a no-op", marks = 1)                     {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.guarantee(F.unit, outer(F.canceled) *> mark))
    },
    new Scenario("outer poll within a guaranteeCase finalizer is a no-op", marks = 1)                 {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.guaranteeCase(F.unit)(_ => outer(F.canceled) *> mark))
    },
    new Scenario("outermost poll within an onCancel finalizer run by cancelation unmasks", marks = 1) {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer =>
          F.uncancelable(inner => F.onCancel(inner(outer(F.canceled)), mark *> outer(F.unit) *> mark))
        )
    },
    new Scenario("outer poll within bracket acquire is a no-op", marks = 1)                           {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.bracket(outer(F.canceled) *> mark)(_ => F.unit)(_ => F.unit))
    },
    new Scenario("poll of an exited mask is a no-op within a bracketCase release", marks = 1)         {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => F.pure(poll))
          .flatMap((poll: Poll[F]) => F.bracketCase(F.unit)(_ => F.unit)((_, _) => poll(F.canceled) *> mark))
    },
    new Scenario("outer poll within bracketFull acquire is a no-op", marks = 1)                       {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.bracketFull(_ => outer(F.canceled) *> mark)(_ => F.unit)((_, _) => F.unit))
    },
    new Scenario("outer poll within bracketFull use unmasks", marks = 0)                              {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.bracketFull(_ => F.unit)(_ => outer(F.canceled) *> mark)((_, _) => F.unit))
    },
    new Scenario("outer poll within bracketFull release is a no-op", marks = 1)                       {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.bracketFull(_ => F.unit)(_ => F.unit)((_, _) => outer(F.canceled) *> mark))
    },
    new Scenario("outermost poll within a bracketFull release run by cancelation unmasks", marks = 1) {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable { outer =>
          F.uncancelable { inner =>
            F.bracketFull(_ => F.unit)(_ => inner(outer(F.canceled)))((_, _) => mark *> outer(F.unit) *> mark)
          }
        }
    }
  )

  val asyncScenarios: List[AsyncScenario] = List(
    new AsyncScenario("outer poll within Resource acquisition is a no-op", marks = 1)                   {
      def run[F[_]](mark: F[Unit])(implicit F: Async[F]): F[Unit] =
        F.uncancelable { outer =>
          Resource.applyFull[F, Unit](_ => (outer(F.canceled) *> mark).map(_ => ((), _ => F.unit))).use(_ => F.unit)
        }
    },
    new AsyncScenario("outer poll within Resource release is a no-op", marks = 1)                       {
      def run[F[_]](mark: F[Unit])(implicit F: Async[F]): F[Unit] =
        F.uncancelable(outer => Resource.make(F.unit)(_ => outer(F.canceled) *> mark).use(_ => F.unit))
    },
    new AsyncScenario("release of a Resource mapped with the outer poll is not interrupted", marks = 1) {
      def run[F[_]](mark: F[Unit])(implicit F: Async[F]): F[Unit] =
        F.uncancelable(poll => Resource.make(F.unit)(_ => mark).mapK(poll).use(_ => F.canceled))
    },
    new AsyncScenario("outer poll within async registration is a no-op", marks = 1)                     {
      def run[F[_]](mark: F[Unit])(implicit F: Async[F]): F[Unit] =
        F.uncancelable(outer =>
          F.async[Unit](cb => outer(F.canceled) *> mark *> F.delay(cb(Right(()))).as(Option.empty[F[Unit]]))
        )
    }
  )

  def observe[F[_], E](program: F[Unit] => F[Unit])(implicit F: GenConcurrent[F, E]): F[(Int, Boolean)] =
    for {
      marks   <- F.ref(0)
      fiber   <- F.start(program(marks.update(_ + 1)))
      outcome <- fiber.join
      count   <- marks.get
    } yield (count, outcome.isError)

  private val F = Async[Task]

  private val status: Task[InterruptStatus] = ZIO.checkInterruptible(ZIO.succeed(_))

  def spec: Spec[Any, Any] = suite("Poll follows cats-effect masking semantics")(
    test("NonEmptyHotswap does not hang after a swap is interrupted while it releases the previous resource") {
      val program =
        for {
          released <- Promise.make[Nothing, Unit]
          gate     <- Promise.make[Nothing, Unit]
          previous  = Resource.make[Task, Unit](ZIO.unit)(_ => released.succeed(()) *> gate.await)
          _        <- NonEmptyHotswap[Task, Unit](previous).use { hotswap =>
                        for {
                          fiber <- hotswap.swap(Resource.unit[Task]).fork
                          _     <- released.await
                          _     <- fiber.interruptFork
                          _     <- gate.succeed(())
                          _     <- fiber.await
                        } yield ()
                      }
        } yield ()
      program.disconnect.timeout(10.seconds).map(completed => assertTrue(completed.isDefined))
    } @@ TestAspect.withLiveClock,
    suite("cats-effect IO (reference)")(
      (scenarios.map(s =>
        test(s.name)(
          fromEffect(observe[CIO, Throwable](s.run(_, new RuntimeException: Throwable)))
            .map(r => assertTrue(r == ((s.marks, false))))
        )
      ) ++
        asyncScenarios.map(s =>
          test(s.name)(fromEffect(observe[CIO, Throwable](s.run(_))).map(r => assertTrue(r == ((s.marks, false)))))
        ))*
    ),
    suite("Task")(
      (scenarios.map(s =>
        test(s.name)(
          observe[Task, Throwable](s.run(_, new RuntimeException: Throwable))
            .map(r => assertTrue(r == ((s.marks, false))))
        )
      ) ++
        asyncScenarios
          .map(s => test(s.name)(observe[Task, Throwable](s.run(_)).map(r => assertTrue(r == ((s.marks, false)))))))*
    ),
    suite("IO[Int, _] with Cause[Int] errors")(
      scenarios.map { s =>
        import zio.interop.catz.generic.*
        test(s.name)(
          observe[IO[Int, _], Cause[Int]](s.run(_, Cause.fail(1))).map(r => assertTrue(r == ((s.marks, false))))
        )
      }*
    ),
    suite("ZIO interaction")(
      test("mask depth is restored after an interruption caught within the mask") {
        observe[Task, Throwable] { mark =>
          F.uncancelable { outer =>
            F.uncancelable { inner =>
              inner(outer(F.canceled)).catchAllCause(_ => ZIO.unit) *> F.uncancelable(_ => outer(F.canceled)) *> mark
            }
          }
        }.map(r => assertTrue(r == ((1, false))))
      },
      test("Resource#toScopedZIO acquisition and release follow the cats mask") {
        for {
          acquisition <- observe[Task, Throwable] { mark =>
                           F.uncancelable(outer =>
                             ZIO.scoped(
                               Resource
                                 .applyFull[Task, Unit](_ =>
                                   (outer(F.canceled) *> mark).as(((), (_: Resource.ExitCase) => ZIO.unit))
                                 )
                                 .toScopedZIO
                             )
                           )
                         }
          release     <-
            observe[Task, Throwable] { mark =>
              F.uncancelable(poll => ZIO.scoped(Resource.make(F.unit)(_ => mark).mapK(poll).toScopedZIO *> F.canceled))
            }
          canceled    <-
            observe[Task, Throwable] { mark =>
              F.uncancelable { outer =>
                F.uncancelable { inner =>
                  ZIO.scoped(
                    Resource.make(F.unit)(_ => mark *> outer(F.unit) *> mark).toScopedZIO *> inner(outer(F.canceled))
                  )
                }
              }
            }
        } yield assertTrue(acquisition == ((1, false)), release == ((1, false)), canceled == ((1, false)))
      },
      test("native forks inherit the mask, so the outer poll unmasks within them") {
        F.uncancelable { poll =>
          poll(status).timeout(1.minute) <*> ZIO.foreachPar(List(1, 2))(_ => poll(status)) <*> poll(status).zipPar(
            poll(status)
          )
        }.map { case (timed, par, zipped) =>
          assertTrue(timed.contains(InterruptStatus.Interruptible)) &&
          assertTrue(par.forall(_ == InterruptStatus.Interruptible)) &&
          assertTrue(zipped == ((InterruptStatus.Interruptible, InterruptStatus.Interruptible)))
        }
      },
      test("outer poll within a nested mask of a native fork is a no-op") {
        F.uncancelable(poll => F.uncancelable(_ => poll(status)).fork.flatMap(_.join))
          .map(s => assertTrue(s == InterruptStatus.Uninterruptible))
      },
      test("joining native and cats fibers does not change the joiner's mask") {
        F.uncancelable { poll =>
          val masked = F.uncancelable(_ => ZIO.unit)
          masked.fork.flatMap(_.join) *> ZIO.foreachParDiscard(List(1, 2))(_ => masked) *> masked.zipPar(masked) *>
            masked.raceFirst(masked) *> F.both(masked, masked) *> poll(status)
        }.map(s => assertTrue(s == InterruptStatus.Interruptible))
      },
      test("cats Parallel branches start unmasked") {
        F.uncancelable(_ =>
          F.uncancelable(inner =>
            (inner(status), inner(status)).parTupled <*> List(inner(status), inner(status)).parSequence
          )
        ).map { case (left, right, sequenced) =>
          assertTrue(
            left == InterruptStatus.Interruptible,
            right == InterruptStatus.Interruptible,
            sequenced == List(InterruptStatus.Interruptible, InterruptStatus.Interruptible)
          )
        }
      },
      test("native masks within a polled region are respected by cats masks nested within them") {
        val polled = F.uncancelable(q => q(status))
        for {
          released  <- Promise.make[Nothing, InterruptStatus]
          masks     <- F.uncancelable { poll =>
                         poll(ZIO.uninterruptibleMask(restore => polled.zip(restore(polled))))
                       }
          bracketed <- F.uncancelable { poll =>
                         poll(
                           ZIO.acquireReleaseWith(polled)(_ => polled.flatMap(released.succeed).orDie)(acquired =>
                             polled.map((acquired, _))
                           )
                         )
                       }
          inRelease <- released.await
        } yield assertTrue(
          masks == ((InterruptStatus.Uninterruptible, InterruptStatus.Interruptible)),
          bracketed == ((InterruptStatus.Uninterruptible, InterruptStatus.Interruptible)),
          inRelease == InterruptStatus.Uninterruptible
        )
      },
      test("native masks are not tracked: a cats poll within them still unmasks (unlike cats-effect)") {
        for {
          released  <- Promise.make[Nothing, InterruptStatus]
          inNative  <- F.uncancelable(poll => ZIO.uninterruptible(poll(status)))
          _         <- F.uncancelable(poll =>
                         ZIO.acquireReleaseWith(ZIO.unit)(_ => poll(status).flatMap(released.succeed).orDie)(_ => ZIO.unit)
                       )
          inRelease <- released.await
          inAcquire <- Resource
                         .makeFull[Task, InterruptStatus](poll => ZIO.uninterruptible(poll(status)))(_ => ZIO.unit)
                         .use(ZIO.succeed(_))
        } yield assertTrue(
          inNative == InterruptStatus.Interruptible,
          inRelease == InterruptStatus.Interruptible,
          inAcquire == InterruptStatus.Interruptible
        )
      }
    )
  )
}
