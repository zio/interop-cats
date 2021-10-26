package zio.interop

import cats.effect.{ IO as CIO, LiftIO }
import cats.effect.kernel.{ Concurrent, Resource }
import zio.interop.catz.*
import zio.test.*
import zio.{ Promise, Task }

object CatsInteropSpec extends CatsRunnableSpec {
  def spec = suite("Cats interop")(
    test("cats fiber wrapped in Resource can be canceled") {
      for {
        promise <- Promise.make[Nothing, Int]
        resource = Resource.make(Concurrent[Task].start(promise.succeed(1) *> Task.never))(_.cancel)
        _       <- resource.use(_ => promise.await)
      } yield assertCompletes
    },
    test("cats IO lifted to ZIO can be canceled") {
      val lift: LiftIO[Task] = implicitly

      for {
        promise1 <- lift.liftIO(CIO.deferred[Unit])
        promise2 <- lift.liftIO(CIO.deferred[Unit])
        fiber    <- lift.liftIO((promise1.complete(()) *> CIO.never).onCancel(promise2.complete(()).void)).forkDaemon
        _        <- lift.liftIO(promise1.get)
        _        <- fiber.interrupt
      } yield assertCompletes
    }
  )
}
