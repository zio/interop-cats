package zio.interop

import cats.effect.{ Concurrent, ContextShift, IO => CIO }
import cats.implicits._
import zio.test.Assertion._
import zio.test._
import zio.test.interop.catz.test._
import zio.Runtime

import scala.concurrent.ExecutionContext.global

object catzQueueSpec extends ZIOSpecDefault {

  def boundedQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[Any]): F[TestResult] =
    for {
      q  <- Queue.bounded[F, Int](1)
      _  <- q.offer(1)
      r1 <- q.takeAll
      _  <- q.offer(2)
      r2 <- q.takeAll
    } yield assert(r1)(equalTo(List(1))) && assert(r2)(equalTo(List(2)))

  def droppingQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[Any]): F[TestResult] =
    for {
      q <- Queue.dropping[F, Int](2)
      _ <- q.offerAll(List(1, 2, 3))
      r <- q.takeAll
    } yield assert(r)(equalTo(List(1, 2)))

  def slidingQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[Any]): F[TestResult] =
    for {
      q <- Queue.sliding[F, Int](2)
      _ <- q.offerAll(List(1, 2, 3, 4))
      r <- q.takeAll
    } yield assert(r)(equalTo(List(3, 4)))

  def unboundedQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[Any]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, Int]
      expected = Range.inclusive(0, 100)
      _        <- q.offerAll(expected)
      actual   <- q.takeAll
    } yield assert(actual)(equalTo(expected.toList))

  def spec = suite("catzQueueSpec")(
    testF("can use a bounded queue from Cats Effect IO") {
      implicit val r: Runtime[Any]      = Runtime.default
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      boundedQueueTest[CIO]
    },
    testF("can use a dropping queue from Cats Effect IO") {
      implicit val r: Runtime[Any]      = Runtime.default
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      droppingQueueTest[CIO]
    },
    testF("can use a sliding queue from Cats Effect IO") {
      implicit val r: Runtime[Any]      = Runtime.default
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      slidingQueueTest[CIO]
    },
    testF("can use an unbounded queue from Cats Effect IO") {
      implicit val r: Runtime[Any]      = Runtime.default
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      unboundedQueueTest[CIO]
    }
  )
}
