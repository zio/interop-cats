package zio.interop

import cats.effect.{ Concurrent, Resource }
import zio.{ Promise, Task }
import zio.interop.catz._
import zio.test._

class CatsInteropSpec extends catzSpecZIOBase {

  test("cats fiber wrapped in Resource can be canceled") {
    for {
      p        <- Promise.make[Nothing, Int]
      resource = Resource.make(Concurrent[Task].start(p.succeed(1) *> Task.never))(_.cancel)
      _        <- resource.use(_ => p.await)
    } yield assertCompletes
  }
}
