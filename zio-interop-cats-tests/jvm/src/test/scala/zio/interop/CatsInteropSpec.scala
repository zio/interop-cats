package zio.interop

import cats.effect.{ Async, IO as CIO, LiftIO }
import cats.effect.kernel.{ Concurrent, Resource }
import zio.interop.catz.*
import zio.test.*
import zio.{ Promise, Task, ZIO }

object CatsInteropSpec extends CatsRunnableSpec {
  def spec = suite("Cats interop")(
    test("cats fiber wrapped in Resource can be canceled") {
      for {
        promise <- Promise.make[Nothing, Int]
        resource = Resource.make(Concurrent[Task].start(promise.succeed(1) *> ZIO.never))(_.cancel)
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
    },
    test("ZIO respects Async#async cancel finalizer") {
      def test[F[_]](implicit F: Async[F]) = {
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
