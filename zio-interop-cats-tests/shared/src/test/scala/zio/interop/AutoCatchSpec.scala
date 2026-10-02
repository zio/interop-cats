package zio.interop

import cats.effect.kernel.{ Async, Outcome }
import zio.*
import zio.test.*

object AutoCatchSpec extends ZIOSpecDefault {

  private final class TestException(message: String) extends RuntimeException(message)

  private val ex  = new TestException("boom")
  private val ex2 = new TestException("boom2")

  private def throwing[A](t: Throwable): A = throw t

  private def recoversDefects(F: Async[Task]) = suite("recovers from defects")(
    test("handleErrorWith recovers from a throw in map") {
      F.handleErrorWith(F.map(F.unit)(_ => throwing[Int](ex)))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("handleErrorWith recovers from a throw in flatMap") {
      F.handleErrorWith(F.flatMap(F.unit)(_ => throwing[Task[Int]](ex)))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("handleErrorWith recovers from ZIO.die") {
      F.handleErrorWith(ZIO.die(ex): Task[Int])(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("handleErrorWith recovers from a throw in a by-name whenA argument") {
      F.handleErrorWith(F.whenA(true)(throwing[Task[Int]](ex)).as(0))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("handleErrorWith recovers from a throw in uncancelable body") {
      F.handleErrorWith(F.uncancelable(_ => throwing[Task[Int]](ex)))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("handleErrorWith recovers from a throw in an error handler") {
      F.handleErrorWith(F.handleErrorWith(F.raiseError[Int](ex2))(_ => throwing[Task[Int]](ex)))(e =>
        F.pure(if (e eq ex) 1 else 2)
      ).map(res => assertTrue(res == 1))
    },
    test("attempt returns the defect as Left") {
      F.attempt(F.map(F.unit)(_ => throwing[Int](ex))).map(res => assertTrue(res == Left(ex)))
    },
    test("recover recovers a matching defect") {
      F.recover(F.map(F.unit)(_ => throwing[Int](ex))) { case _: TestException => 1 }
        .map(res => assertTrue(res == 1))
    },
    test("recoverWith recovers a matching defect") {
      F.recoverWith(F.map(F.unit)(_ => throwing[Int](ex))) { case _: TestException => F.pure(1) }
        .map(res => assertTrue(res == 1))
    },
    test("recoverWith preserves the defect if the partial function does not match") {
      F.recoverWith(ZIO.die(ex): Task[Int]) { case _: IllegalStateException => F.pure(1) }
        .exit
        .map(exit => assertTrue(exit == Exit.die(ex)))
    },
    test("adaptError transforms a matching defect") {
      F.adaptError(ZIO.die(ex): Task[Int]) { case _: TestException => ex2 }
        .exit
        .map(exit => assertTrue(exit == Exit.fail(ex2)))
    },
    test("redeemWith recovers from a defect") {
      F.redeemWith(F.map(F.unit)(_ => throwing[Int](ex)))(e => F.pure(if (e eq ex) 1 else 2), _ => F.pure(3))
        .map(res => assertTrue(res == 1))
    },
    test("rethrow . attempt converts a defect into a typed failure") {
      F.rethrow(F.attempt(ZIO.die(ex): Task[Int])).exit.map(exit => assertTrue(exit == Exit.fail(ex)))
    },
    test("onError observes a defect") {
      for {
        ref  <- Ref.make(Option.empty[Throwable])
        exit <- F.onError(ZIO.die(ex): Task[Int]) { case e => ref.set(Some(e)) }.exit
        seen <- ref.get
      } yield assertTrue(seen == Some(ex), exit == Exit.fail(ex))
    },
    test("guaranteeCase observes a defect as Outcome.Errored") {
      for {
        ref  <- Ref.make(Option.empty[Throwable])
        _    <- F.guaranteeCase(ZIO.die(ex): Task[Int]) {
                  case Outcome.Errored(e) => ref.set(Some(e))
                  case _                  => ZIO.unit
                }.exit
        seen <- ref.get
      } yield assertTrue(seen == Some(ex))
    },
    test("a typed failure takes precedence over a defect in a composite cause") {
      F.attempt(ZIO.failCause(Cause.fail(ex2) && Cause.die(ex)): Task[Int]).map(res => assertTrue(res == Left(ex2)))
    },
    test("multiple defects are recovered as a FiberFailure carrying the whole cause") {
      val cause = Cause.die(ex) ++ Cause.die(ex2)
      F.attempt(ZIO.failCause(cause): Task[Int]).map {
        case Left(e: FiberFailure) => assertTrue(e.cause == cause)
        case other                 => assertNever(s"unexpected $other")
      }
    },
    test("handleErrorWith recovers from a throw in parTraverse") {
      val tasks = List(F.map(F.unit)(_ => throwing[Int](ex)), F.never[Int])
      F.handleErrorWith(cats.Parallel.parSequence(tasks)(cats.Traverse[List], catz.core.parallelInstance))(e =>
        F.pure(
          if (e eq ex) List(1)
          else List(2)
        )
      ).map(res => assertTrue(res == List(1)))
    },
    test("a defect accompanied by an interruption of another fiber is recovered") {
      F.attempt(ZIO.failCause(Cause.die(ex) && Cause.interrupt(FiberId(1, 1, Trace.empty))): Task[Int])
        .map(res => assertTrue(res == Left(ex)))
    }
  )

  private def suspendsByNameArguments(F: Async[Task]) = suite("suspends by-name arguments")(
    test("unlessA") {
      F.handleErrorWith(F.unlessA(false)(throwing[Task[Int]](ex)).as(0))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("raiseUnless") {
      F.handleErrorWith(F.raiseUnless(false)(throwing[Throwable](ex)).as(0))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("fromOption") {
      F.handleErrorWith(F.fromOption[Int](None, throwing[Throwable](ex)))(e => F.pure(if (e eq ex) 1 else 2))
        .map(res => assertTrue(res == 1))
    },
    test("fromOption fails with ifEmpty") {
      F.fromOption[Int](None, ex2).exit.map(exit => assertTrue(exit == Exit.fail(ex2)))
    }
  )

  private def respectsCancelation(F: Async[Task]) = suite("respects cancelation")(
    test("a defect raised while the fiber is being canceled is not recovered") {
      for {
        ref    <- Ref.make(false)
        fiber  <-
          F.uncancelable { _ =>
            F.productR(F.canceled)(
              F.handleErrorWith(ZIO.descriptorWith(d => ZIO.failCause(Cause.die(ex) && Cause.interrupt(d.id))))(_ =>
                ref.set(true)
              )
            )
          }.fork
        exit   <- fiber.await
        called <- ref.get
      } yield assertTrue(!called, exit.isInterrupted)
    },
    test("a defect inside an uncancelable region with a pending cancelation is recovered") {
      for {
        ref    <- Ref.make(false)
        fiber  <- F.uncancelable(_ => F.productR(F.canceled)(F.handleErrorWith(ZIO.die(ex))(_ => ref.set(true)))).fork
        exit   <- fiber.await
        called <- ref.get
      } yield assertTrue(called, exit.isInterrupted)
    }
  )

  def spec: Spec[Any, Any] = suite("AutoCatchSpec")(
    suite("catz.autocatch")(
      recoversDefects(zio.interop.catz.autocatch.asyncInstanceAutoCatch[Any]),
      respectsCancelation(zio.interop.catz.autocatch.asyncInstanceAutoCatch[Any]),
      suspendsByNameArguments(zio.interop.catz.autocatch.asyncInstanceAutoCatch[Any]),
      test("summoned instances are the autocatch instances, combined with catz.core") {
        import cats.MonadError
        import cats.effect.kernel.{ Concurrent, Sync, Temporal }
        import zio.interop.catz.autocatch.*
        import zio.interop.catz.core.*

        val instance = asyncInstanceAutoCatch[String]
        assertTrue(
          Async[RIO[String, _]] eq instance,
          Sync[RIO[String, _]] eq instance,
          Temporal[RIO[String, _]] eq instance,
          Concurrent[RIO[String, _]] eq instance,
          MonadError[RIO[String, _], Throwable] eq instance,
          Async[Task] eq instance,
          implicitly[cats.Parallel[RIO[String, _]]] eq parallelInstance[String, Throwable]
        )
      }
    ),
    suite("catz")(
      test("handleErrorWith does not recover from a defect") {
        val F = zio.interop.catz.asyncInstance[Any]
        F.handleErrorWith(F.map(F.unit)(_ => throwing[Int](ex)))(_ => F.pure(1))
          .exit
          .map(exit => assertTrue(exit == Exit.die(ex)))
      },
      test("attempt does not recover from a defect") {
        val F = zio.interop.catz.asyncInstance[Any]
        F.attempt(ZIO.die(ex): Task[Int]).exit.map(exit => assertTrue(exit == Exit.die(ex)))
      }
    )
  )
}
