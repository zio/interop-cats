package zio.interop

import cats.effect.std.Dispatcher
import cats.effect.unsafe.{ IORuntime, IORuntimeConfig, Scheduler }
import cats.effect.{ Async, IO => CIO }
import cats.implicits._
import zio.blocking.Blocking
import zio.test.Assertion._
import zio.test._
import zio.test.interop.catz.test._
import zio.{ Runtime, ZEnv }

object catzQueueSpec extends DefaultRunnableSpec {

  def boundedQueueTest[F[+_]: Async](implicit R: Runtime[ZEnv]): F[TestResult] =
    for {
      q  <- Queue.bounded[F, Int](1)
      _  <- q.offer(1)
      r1 <- q.takeAll
      _  <- q.offer(2)
      r2 <- q.takeAll
    } yield assert(r1)(equalTo(List(1))) && assert(r2)(equalTo(List(2)))

  def droppingQueueTest[F[+_]: Async](implicit R: Runtime[ZEnv]): F[TestResult] =
    for {
      q <- Queue.dropping[F, Int](2)
      _ <- q.offerAll(List(1, 2, 3))
      r <- q.takeAll
    } yield assert(r)(equalTo(List(1, 2)))

  def slidingQueueTest[F[+_]: Async](implicit R: Runtime[ZEnv]): F[TestResult] =
    for {
      q <- Queue.sliding[F, Int](2)
      _ <- q.offerAll(List(1, 2, 3, 4))
      r <- q.takeAll
    } yield assert(r)(equalTo(List(3, 4)))

  def unboundedQueueTest[F[+_]: Async](implicit R: Runtime[ZEnv]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, Int]
      expected = Range.inclusive(0, 100)
      _        <- q.offerAll(expected)
      actual   <- q.takeAll
    } yield assert(actual)(equalTo(expected.toList))

  def contramapQueueTest[F[+_]: Async](implicit R: Runtime[ZEnv]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, String]
      q1       = q.contramap((i: Int) => i.toString)
      data     = Range.inclusive(0, 100)
      _        <- q1.offerAll(data)
      actual   <- q1.takeAll
      expected = data.map(_.toString)
    } yield assert(actual)(equalTo(expected.toList))

  def mapMQueueTest[F[+_]](implicit F: Async[F], D: Dispatcher[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, Int]
      q1       = q.mapM(i => F.pure(i.toString))
      data     = Range.inclusive(0, 100)
      _        <- q1.offerAll(data)
      actual   <- q1.takeAll
      expected = data.map(_.toString)
    } yield assert(actual)(equalTo(expected.toList))

  def spec = {
    implicit val zioRuntime: Runtime[ZEnv] = Runtime.default
    implicit val ioRuntime: IORuntime = Scheduler.createDefaultScheduler() match {
      case (scheduler, shutdown) =>
        IORuntime(
          zioRuntime.platform.executor.asEC,
          zioRuntime.environment.get[Blocking.Service].blockingExecutor.asEC,
          scheduler,
          shutdown,
          IORuntimeConfig()
        )
    }

    implicit val dispatcher: Dispatcher[CIO] =
      Dispatcher[CIO].allocated.unsafeRunSync()._1

    suite("catzQueueSpec")(
      testF("can use a bounded queue from Cats Effect IO")(boundedQueueTest[CIO]),
      testF("can use a dropping queue from Cats Effect IO")(droppingQueueTest[CIO]),
      testF("can use a sliding queue from Cats Effect IO")(slidingQueueTest[CIO]),
      testF("can use an unbounded queue from Cats Effect IO")(unboundedQueueTest[CIO]),
      testF("can contramap a queue from Cats Effect IO")(contramapQueueTest[CIO]),
      testF("can mapM a queue from Cats Effect IO")(mapMQueueTest[CIO])
    )
  }
}
