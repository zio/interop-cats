package zio.test.interop

import cats.effect.IO as CIO
import zio.*
import zio.interop.CatsRunnableSpec
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.test.*
import zio.test.interop.catz.test.*

object TestSpec extends CatsRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] =
    suite("TestSpec")(
      testF("arbitrary effects can be tested") {
        for {
          result <- CIO("Hello from Cats!")
        } yield assert(result)(equalTo("Hello from Cats!"))
      },
      timeout(0.milliseconds) {
        testF("ZIO interruption is tied to F interruption") {
          for {
            _ <- CIO.never[Nothing]
          } yield assertCompletes
        }
      } @@ failing
    )
}
