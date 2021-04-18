package zio.interop

import cats.effect.{ Effect, Concurrent, IO => CIO }
import cats.implicits._
import zio.test.Assertion._
import zio.test._
import zio.test.interop.catz.test._
import zio.{ Runtime, ZEnv }
import zio.internal.Platform

import scala.concurrent.ExecutionContext.global

object catzQueueSpec extends DefaultRunnableSpec {

  def boundedQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q  <- Queue.bounded[F, Int](1)
      _  <- q.offer(1)
      r1 <- q.takeAll
      _  <- q.offer(2)
      r2 <- q.takeAll
    } yield assert(r1)(equalTo(List(1))) && assert(r2)(equalTo(List(2)))

  def droppingQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q <- Queue.dropping[F, Int](2)
      _ <- q.offerAll(List(1, 2, 3))
      r <- q.takeAll
    } yield assert(r)(equalTo(List(1, 2)))

  def slidingQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q <- Queue.sliding[F, Int](2)
      _ <- q.offerAll(List(1, 2, 3, 4))
      r <- q.takeAll
    } yield assert(r)(equalTo(List(3, 4)))

  def unboundedQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, Int]
      expected = Range.inclusive(0, 100)
      _        <- q.offerAll(expected)
      actual   <- q.takeAll
    } yield assert(actual)(equalTo(expected.toList))

  def contramapQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, String]
      q1       = q.contramap((i: Int) => i.toString)
      data     = Range.inclusive(0, 100)
      _        <- q1.offerAll(data)
      actual   <- q1.takeAll
      expected = data.map(_.toString)
    } yield assert(actual)(equalTo(expected.toList))

  def mapMQueueTest[F[+_]](implicit F: Effect[F], R: Runtime[ZEnv]): F[TestResult] =
    for {
      q        <- Queue.unbounded[F, Int]
      q1       = q.mapM(i => F.pure(i.toString))
      data     = Range.inclusive(0, 100)
      _        <- q1.offerAll(data)
      actual   <- q1.takeAll
      expected = data.map(_.toString)
    } yield assert(actual)(equalTo(expected.toList))

  def spec = suite("catzQueueSpec")(
    testF("can use a bounded queue from Cats Effect IO") {
      implicit val r: Runtime[ZEnv]     = Runtime.unsafeFromLayer(ZEnv.live, Platform.default)
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      boundedQueueTest[CIO]
    },
    testF("can use a dropping queue from Cats Effect IO") {
      implicit val r: Runtime[ZEnv]     = Runtime.unsafeFromLayer(ZEnv.live, Platform.default)
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      droppingQueueTest[CIO]
    },
    testF("can use a sliding queue from Cats Effect IO") {
      implicit val r: Runtime[ZEnv]     = Runtime.unsafeFromLayer(ZEnv.live, Platform.default)
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      slidingQueueTest[CIO]
    },
    testF("can use an unbounded queue from Cats Effect IO") {
      implicit val r: Runtime[ZEnv]     = Runtime.unsafeFromLayer(ZEnv.live, Platform.default)
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      unboundedQueueTest[CIO]
    },
    testF("can contramap a queue from Cats Effect IO") {
      implicit val r: Runtime[ZEnv]     = Runtime.unsafeFromLayer(ZEnv.live, Platform.default)
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      contramapQueueTest[CIO]
    },
    testF("can mapM a queue from Cats Effect IO") {
      implicit val r: Runtime[ZEnv]     = Runtime.unsafeFromLayer(ZEnv.live, Platform.default)
      implicit val c: ContextShift[CIO] = CIO.contextShift(global)
      mapMQueueTest[CIO]
    }
  )
}
