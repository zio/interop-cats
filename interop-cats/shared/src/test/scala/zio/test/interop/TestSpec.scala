package zio.test.interop

import cats.effect.IO
import zio.duration._
import zio.interop.CatsRunnableSpec
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment.TestEnvironment
import zio.test.interop.catz.test._

object TestSpec extends CatsRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] =
    suite("TestSpec")(
      testF("arbitrary effects can be tested") {
        for {
          result <- IO("Hello from Cats!")
        } yield assert(result)(equalTo("Hello from Cats!"))
      },
      timeout(0.milliseconds) {
        testF("ZIO interruption is tied to F interruption") {
          for {
            _ <- IO.never[Nothing]
          } yield assertCompletes
        }
      } @@ failing
    )
}
