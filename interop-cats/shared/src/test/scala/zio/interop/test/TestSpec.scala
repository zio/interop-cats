package zio.interop.test

import cats.effect.IO
import zio.duration._
import zio.interop.catz.test._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZIO

import TestSpecUtils._

object TestSpec
    extends DefaultRunnableSpec(
      suite("TestSpec")(
        testF("arbitrary effects can be tested") {
          for {
            result <- IO("Hello from Cats!")
          } yield assert(result, equalTo("Hello from Cats!"))
        },
        timeout(0.milliseconds) {
          testF("ZIO interruption is tied to F interruption") {
            for {
              _ <- IO.never
            } yield assert((), anything)
          }
        } @@ failure
      )
    )

object TestSpecUtils {
  val failure: TestAspect[Nothing, Any, Nothing, Any, Unit, Unit] =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Unit, Unit] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Unit <: Unit](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        test.foldCauseM(
          _ => ZIO.succeed(TestSuccess.Succeeded(AssertResult.unit)),
          _ => ZIO.fail(TestFailure.Runtime(zio.Cause.die(new RuntimeException("expected failure"))))
        )
    }
}
