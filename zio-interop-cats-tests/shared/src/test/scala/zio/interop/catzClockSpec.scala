import java.util.concurrent.TimeUnit

import cats.effect.Timer
import fs2.Stream
import zio._
import zio.clock._
import zio.duration._
import zio.interop.catz._
import zio.test.environment._
import zio.test.TestAspect._
import zio.test._

import scala.concurrent.duration.FiniteDuration

object catzClockSpec extends DefaultRunnableSpec {

  def spec =
    suite("catzClockSpec") {
      testM("Timer can be constructed from ZIO Clock") {
        ZIO.environment[Clock].flatMap { clock =>
          implicit val timer: Timer[Task] =
            clock.get.toTimer

          val stream: Stream[Task, Int] =
            Stream.eval(Task.effect(1)).delayBy(FiniteDuration(10, TimeUnit.DAYS))

          for {
            fiber <- stream.compile.drain.fork
            _     <- TestClock.adjust(10.days)
            _     <- fiber.join
          } yield assertCompletes
        }
      } @@ timeout(60.seconds)
    }
}
