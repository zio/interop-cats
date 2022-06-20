package zio.interop

import cats.effect.{ Async, IO as CIO, LiftIO, Outcome }
import cats.effect.kernel.{ Concurrent, Resource }
import zio.interop.catz.*
import zio.test.*
import zio.{ Promise, Task, ZIO }

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
    },
    testM("onCancel is not triggered by ZIO.parTraverse + ZIO.fail https://github.com/zio/zio/issues/6911") {
      val F = Concurrent[Task]

      for {
        counter <- F.ref("")
        _       <- F.guaranteeCase(
                     F.onError(
                       F.onCancel(
                         ZIO.collectAllPar(
                           List(
                             ZIO.unit.forever,
                             counter.update(_ + "A") *> ZIO.fail(new RuntimeException("x")).unit
                           )
                         ),
                         counter.update(_ + "1")
                       )
                     ) { case _ => counter.update(_ + "B") }
                   ) {
                     case Outcome.Errored(_)   => counter.update(_ + "C")
                     case Outcome.Canceled()   => counter.update(_ + "2")
                     case Outcome.Succeeded(_) => counter.update(_ + "3")
                   }.run
        res     <- counter.get
      } yield assertTrue(!res.contains("1")) && assertTrue(res == "ABC")
    },
    testM("onCancel is not triggered by ZIO.parTraverse + ZIO.die https://github.com/zio/zio/issues/6911") {
      val F = Concurrent[Task]

      for {
        counter <- F.ref("")
        _       <- F.guaranteeCase(
                     F.onError(
                       F.onCancel(
                         ZIO.collectAllPar(
                           List(
                             ZIO.unit.forever,
                             counter.update(_ + "A") *> ZIO.die(new RuntimeException("x")).unit
                           )
                         ),
                         counter.update(_ + "1")
                       )
                     ) { case _ => counter.update(_ + "B") }
                   ) {
                     case Outcome.Errored(_)   => counter.update(_ + "C")
                     case Outcome.Canceled()   => counter.update(_ + "2")
                     case Outcome.Succeeded(_) => counter.update(_ + "3")
                   }.run
        res     <- counter.get
      } yield assertTrue(!res.contains("1")) && assertTrue(res == "ABC")
    },
    testM("onCancel is not triggered by ZIO.parTraverse + ZIO.interrupt https://github.com/zio/zio/issues/6911") {
      val F = Concurrent[Task]

      for {
        counter <- F.ref("")
        _       <- F.guaranteeCase(
                     F.onError(
                       F.onCancel(
                         ZIO.collectAllPar(
                           List(
                             ZIO.unit.forever,
                             counter.update(_ + "A") *> ZIO.interrupt.unit
                           )
                         ),
                         counter.update(_ + "1")
                       )
                     ) { case _ => counter.update(_ + "B") }
                   ) {
                     case Outcome.Errored(_)   => counter.update(_ + "C")
                     case Outcome.Canceled()   => counter.update(_ + "2")
                     case Outcome.Succeeded(_) => counter.update(_ + "3")
                   }.run
        res     <- counter.get
      } yield assertTrue(!res.contains("1")) && assertTrue(res == "ABC")
    }
  )
}
