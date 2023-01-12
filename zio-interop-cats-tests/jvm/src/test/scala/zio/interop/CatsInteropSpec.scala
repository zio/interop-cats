package zio.interop

import cats.effect.{ Async, IO as CIO, LiftIO, Outcome }
import cats.effect.kernel.{ Concurrent, Resource }
import zio.interop.catz.*
import zio.test.*
import zio.*

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
    },
    test("onCancel is not triggered by ZIO.parTraverse + ZIO.fail https://github.com/zio/zio/issues/6911") {
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
                   }.exit
        res     <- counter.get
      } yield assertTrue(!res.contains("1")) && assertTrue(res == "ABC")
    },
    test("onCancel is not triggered by ZIO.parTraverse + ZIO.die https://github.com/zio/zio/issues/6911") {
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
                   }.exit
        res     <- counter.get
      } yield assertTrue(!res.contains("1")) && assertTrue(res == "AC")
    },
    test("onCancel is not triggered by ZIO.parTraverse + ZIO.interrupt https://github.com/zio/zio/issues/6911") {
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
                   }.exit
        res     <- counter.get
      } yield assertTrue(!res.contains("1")) && assertTrue(res == "AC")
    },
    test(
      "onCancel is triggered when a fiber executing ZIO.parTraverse + ZIO.fail is interrupted and the inner typed" +
        " error is, unlike ZIO 1, preserved in final Cause (in ZIO 1 Fail & Interrupt nodes CAN both exist in Cause after external interruption)"
    ) {
      val F = Concurrent[Task]

      def println(s: String): Unit = {
        val _ = s
      }

      for {
        latch1  <- F.deferred[Unit]
        latch2  <- F.deferred[Unit]
        latch3  <- F.deferred[Unit]
        counter <- F.ref("")
        cause   <- F.ref(Option.empty[Cause[Throwable]])
        fiberId <- ZIO.fiberId
        fiber   <- F.guaranteeCase(
                     F.onError(
                       F.onCancel(
                         ZIO
                           .collectAllPar(
                             List(
                               F.onCancel(
                                 ZIO.never,
                                 ZIO.succeed(println("A")) *> latch2.complete(()).unit
                               ).onExit(_ => ZIO.succeed(println("XA"))),
                               (latch1.complete(()) *> latch3.get *> ZIO.succeed(println("C"))).uninterruptible,
                               counter.update(_ + "A") *>
                                 latch1.get *>
                                 ZIO.succeed(println("B")) *> ZIO.fail(new RuntimeException("The_Error")).unit
                             )
                           )
                           .onExit {
                             case Exit.Success(_) => ZIO.unit
                             case Exit.Failure(c) => cause.set(Some(c)).orDie
                           },
                         counter.update(_ + "B")
                       )
                     ) { case _ => counter.update(_ + "1") }
                   ) {
                     case Outcome.Errored(_)   => counter.update(_ + "2")
                     case Outcome.Canceled()   => counter.update(_ + "C")
                     case Outcome.Succeeded(_) => counter.update(_ + "3")
                   }.fork
        _        = println("x1")
        _       <- latch2.get
        _        = println("x2")
        _       <- fiber.interruptFork
        _        = println("x3")
        _       <- latch3.complete(())
        _       <- fiber.interrupt
        _        = println("x4")
        res     <- counter.get
        cause   <- cause.get
      } yield assertTrue(!res.contains("1")) &&
        assertTrue(res == "ABC") &&
        assertTrue(cause.isDefined) &&
        assertTrue(cause.get.prettyPrint.contains("The_Error")) &&
        assertTrue(cause.get.interruptors.contains(fiberId))
    },
    test("F.canceled.toEffect results in CancellationException, not BoxedException") {
      val F = Concurrent[Task]

      val exception: Option[Throwable] =
        try {
          F.canceled.toEffect[cats.effect.IO].unsafeRunSync()
          None
        } catch {
          case t: Throwable => Some(t)
        }

      assertTrue(
        !exception.get.getMessage.contains("Boxed Exception") &&
          exception.get.getMessage.contains("The fiber was canceled")
      )
    }
  )
}
