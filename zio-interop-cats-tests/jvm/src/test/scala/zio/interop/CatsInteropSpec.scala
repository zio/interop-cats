package zio.interop

import cats.effect.{ Async, IO as CIO, LiftIO }
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
    },
    testM("ZIO respects Async#async cancel finalizer") {
      def test[F[_]](implicit F: Async[F]): F[Assert] = {
        import cats.syntax.all.*
        import cats.effect.syntax.all.*
        for {
          counter <- F.ref(0)
          latch   <- F.deferred[Unit]
          fiber   <- F.start(
                       F.async[Unit] { _ =>
                         for {
                           _ <- latch.complete(())
                           _ <- counter.update(_ + 1)
                         } yield Some(counter.update(_ + 1))
                       }.forceR(counter.update(_ + 9000))
                     )
          _       <- latch.get
          _       <- fiber.cancel
          res     <- counter.get
        } yield assertTrue(res == 2)
      }

      for {
        sanityCheckCIO <- fromEffect(test[CIO])
        zioResult      <- test[Task]
      } yield zioResult && sanityCheckCIO
    }
  )
}
