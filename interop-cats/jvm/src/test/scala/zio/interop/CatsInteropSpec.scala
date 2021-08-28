package zio.interop

import cats.effect.{ IO as CIO, LiftIO }
import cats.effect.kernel.{ Concurrent, Resource }
import zio.interop.catz.*
import zio.test.*
import zio.{ Promise, Task }

object CatsInteropSpec extends CatsRunnableSpec {
  def spec = suite("Cats interop")(
    test("cats fiber wrapped in Resource can be canceled") {
      val io = for {
        promise <- Promise.make[Nothing, Int]
        resource = Resource.make(Concurrent[Task].start(promise.succeed(1) *> Task.never))(_.cancel)
        _       <- resource.use(_ => promise.await)
      } yield 0

      assertTrue(zioRuntime.unsafeRun(io) == 0)
    },
    test("cats IO lifted to ZIO can be canceled") {
      val lift: LiftIO[Task] = implicitly

      val io = for {
        promise <- lift.liftIO(CIO.deferred[Int])
        fiber   <- lift.liftIO(CIO.never.onCancel(promise.complete(0).void)).forkDaemon
        _       <- fiber.interruptFork
        result  <- lift.liftIO(promise.get)
      } yield result

      assertTrue(zioRuntime.unsafeRun(io) == 0)
    }
  )
}
