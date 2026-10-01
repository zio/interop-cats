package zio.interop

import cats.effect.kernel.{ GenConcurrent, Poll, Resource }
import cats.effect.IO as CIO
import cats.syntax.all.*
import zio.interop.catz.*
import zio.test.*
import zio.*

/**
 * Checks that `MonadCancel#uncancelable` follows the masking rules documented
 * by cats-effect (https://github.com/zio/interop-cats/issues/562):
 * masks stack, each poll only undoes its own mask when that mask is the
 * innermost one on the current fiber, and is otherwise a no-op.
 *
 * Every scenario is run against cats-effect `IO`, the reference
 * implementation, and against ZIO, and both results are compared with the
 * expected observation.
 */
object CatsUncancelableSpec extends CatsRunnableSpec {

  /**
   * Whether the effect sequenced after the `canceled` under test was executed,
   * i.e. whether `canceled` was masked.
   *
   * The outcome of the fiber is deliberately not observed: after a masked
   * `canceled`, cats-effect `IO` only observes the cancelation at its next
   * cancelation checkpoint, which the scenarios may not reach before completing.
   */
  final case class Observation(reachedAfterCancel: Boolean)

  final class TestError extends RuntimeException("test error")

  abstract class Scenario(val name: String, val expected: Observation) {
    def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit]
  }

  private val maskedCancel   = Observation(reachedAfterCancel = true)
  private val unmaskedCancel = Observation(reachedAfterCancel = false)

  val scenarios: List[Scenario] = List(
    new Scenario("outer poll applied within a nested mask is a no-op", maskedCancel)                        {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(_ => outer(F.canceled) *> mark))
    },
    new Scenario("poll applied within another fiber is a no-op", maskedCancel)                              {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => F.start(F.uncancelable(_ => poll(F.canceled) *> mark)).flatMap(_.join).void)
    },
    new Scenario("polls applied outermost-first fully unmask", unmaskedCancel)                              {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(inner => inner(outer(F.canceled)) *> mark))
    },
    new Scenario("polls applied in the wrong order are a no-op", maskedCancel)                              {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(inner => outer(inner(F.canceled)) *> mark))
    },
    new Scenario("poll(poll(fa)) unmasks a single mask", unmaskedCancel)                                    {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => poll(poll(F.canceled)) *> mark)
    },
    new Scenario("poll(poll(fa)) is equivalent to poll(fa) within a nested mask", maskedCancel)             {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(_ => F.uncancelable(inner => inner(inner(F.canceled)) *> mark))
    },
    new Scenario("mask is reinstated after a successful poll", maskedCancel)                                {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.uncancelable(inner => inner(F.unit) *> outer(F.canceled) *> mark))
    },
    new Scenario("mask is reinstated after a failed poll", maskedCancel)                                    {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable { outer =>
          F.uncancelable { inner =>
            inner(F.raiseError[Unit](error)).handleError(_ => ()) *> outer(F.canceled) *> mark
          }
        }
    },
    new Scenario("outer poll is effective again after a nested mask failed", unmaskedCancel)                {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable { outer =>
          F.uncancelable(_ => F.raiseError[Unit](error)).handleError(_ => ()) *> outer(F.canceled) *> mark
        }
    },
    new Scenario("poll is effective again after a mask nested within it exited", unmaskedCancel)            {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => outer(F.uncancelable(_ => F.unit)) *> outer(F.canceled) *> mark)
    },
    new Scenario("poll of an exited mask is effective within a new mask of the same depth", unmaskedCancel) {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(poll => F.pure(poll)).flatMap((poll: Poll[F]) => F.uncancelable(_ => poll(F.canceled) *> mark))
    },
    new Scenario("outer poll applied within bracketFull acquire is a no-op", maskedCancel)                  {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.bracketFull(_ => outer(F.canceled) *> mark)(_ => F.unit)((_, _) => F.unit))
    },
    new Scenario("outer poll applied within bracketFull use is effective", unmaskedCancel)                  {
      def run[F[_], E](mark: F[Unit], error: E)(implicit F: GenConcurrent[F, E]): F[Unit] =
        F.uncancelable(outer => F.bracketFull(_ => F.unit)(_ => outer(F.canceled) *> mark)((_, _) => F.unit))
    }
  )

  /** `Resource` is only defined for `Throwable` errors */
  def resourceAcquisition[F[_]](mark: F[Unit])(implicit F: GenConcurrent[F, Throwable]): F[Unit] =
    F.uncancelable { outer =>
      Resource.applyFull[F, Unit](_ => (outer(F.canceled) *> mark).map(_ => ((), _ => F.unit))).use(_ => F.unit)
    }

  def observe[F[_], E](scenario: Scenario, error: E)(implicit F: GenConcurrent[F, E]): F[Observation] =
    observeProgram[F, E](scenario.run(_, error))

  def observeProgram[F[_], E](program: F[Unit] => F[Unit])(implicit F: GenConcurrent[F, E]): F[Observation] =
    for {
      reached <- F.ref(false)
      fiber   <- F.start(program(reached.set(true)))
      _       <- fiber.join
      marked  <- reached.get
    } yield Observation(marked)

  private def observeCIO(scenario: Scenario): Task[Observation] =
    ZIO.fromFuture(_ => observe[CIO, Throwable](scenario, new TestError).unsafeToFuture())

  private def observeTask(scenario: Scenario): Task[Observation] =
    observe[Task, Throwable](scenario, new TestError)

  private def observeGeneric(scenario: Scenario): IO[Int, Observation] = {
    import zio.interop.catz.generic.*
    observe[IO[Int, _], Cause[Int]](scenario, Cause.fail(1))
  }

  private def scenarioSuite(name: String)(observe: Scenario => ZIO[Any, Any, Observation]): Spec[Any, Any] =
    suite(name)(
      scenarios.map(scenario =>
        test(scenario.name)(observe(scenario).map(obs => assertTrue(obs == scenario.expected)))
      )*
    )

  def spec: Spec[Any, Any] = suite("MonadCancel#uncancelable follows cats-effect masking semantics")(
    scenarioSuite("cats-effect IO (reference)")(observeCIO),
    scenarioSuite("Task")(observeTask),
    scenarioSuite("IO[Int, _] with Cause[Int] errors")(observeGeneric),
    test("outer poll applied within Resource acquisition is a no-op") {
      for {
        reference <- ZIO.fromFuture(_ => observeProgram[CIO, Throwable](resourceAcquisition[CIO]).unsafeToFuture())
        actual    <- observeProgram[Task, Throwable](resourceAcquisition[Task])
      } yield assertTrue(reference == maskedCancel, actual == maskedCancel)
    },
    suite("interaction with ZIO interruptibility")(
      test("poll does not unmask within ZIO.uninterruptible") {
        val F = catz.asyncInstance[Any]
        observeProgram[Task, Throwable](mark => ZIO.uninterruptible(F.uncancelable(poll => poll(F.canceled) *> mark)))
          .map(obs => assertTrue(obs == maskedCancel))
      },
      test("poll unmasks within ZIO.interruptible inside the mask") {
        val F = catz.asyncInstance[Any]
        observeProgram[Task, Throwable](mark => F.uncancelable(poll => ZIO.interruptible(poll(F.canceled)) *> mark))
          .map(obs => assertTrue(obs == unmaskedCancel))
      },
      test("mask is reinstated after a poll interrupted within ZIO.interruptible inside the mask") {
        val F = catz.asyncInstance[Any]
        observeProgram[Task, Throwable] { mark =>
          F.uncancelable { outer =>
            F.uncancelable { inner =>
              ZIO.interruptible(inner(outer(F.canceled))).catchAllCause(_ => ZIO.unit) *> outer(F.canceled) *> mark
            }
          }
        }.map(obs => assertTrue(obs == maskedCancel))
      }
    ),
    test("outer poll applied within Resource#toScopedZIO acquisition is a no-op") {
      val F = catz.asyncInstance[Any]
      observeProgram[Task, Throwable] { mark =>
        F.uncancelable { outer =>
          ZIO.scoped {
            Resource
              .applyFull[Task, Unit](_ => (outer(F.canceled) *> mark).as(((), (_: Resource.ExitCase) => ZIO.unit)))
              .toScopedZIO
          }
        }
      }.map(obs => assertTrue(obs == maskedCancel))
    }
  )
}
