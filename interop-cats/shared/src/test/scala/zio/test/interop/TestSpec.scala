package zio.test.interop

import cats.effect.IO
import zio.duration._
import zio.test.interop.catz.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

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
