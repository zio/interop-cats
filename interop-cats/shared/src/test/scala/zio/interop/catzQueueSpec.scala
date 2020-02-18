package zio.interop

import cats.effect.implicits._
import cats.effect.{ Effect, Concurrent, ContextShift, IO => CIO }
import cats.implicits._
import zio.test.Assertion._
import zio.test._
import zio.test.interop.catz.test._
import zio.{ DefaultRuntime, Runtime, ZEnv }

import scala.concurrent.ExecutionContext.global

object catzQueueSpec
    extends DefaultRunnableSpec({
      def boundedQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
        for {
          q  <- Queue.bounded[F, Any, Int](1)
          _  <- q.offer(1)
          f  <- q.offer(2).start
          r1 <- q.takeAll
          _  <- f.join
          r2 <- q.takeAll
        } yield assert(r1, equalTo(List(1))) && assert(r2, equalTo(List(2)))

      def droppingQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
        for {
          q <- Queue.dropping[F, Any, Int](2)
          _ <- q.offerAll(List(1, 2, 3))
          r <- q.takeAll
        } yield assert(r, equalTo(List(1, 2)))

      def slidingQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
        for {
          q <- Queue.sliding[F, Any, Int](2)
          _ <- q.offerAll(List(1, 2, 3, 4))
          r <- q.takeAll
        } yield assert(r, equalTo(List(3, 4)))

      def unboundedQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
        for {
          q        <- Queue.unbounded[F, Any, Int]
          expected = Range.inclusive(0, 100)
          _        <- q.offerAll(expected)
          actual   <- q.takeAll
        } yield assert(actual, equalTo(expected))

      def contramapQueueTest[F[+_]](implicit F: Concurrent[F], R: Runtime[ZEnv]): F[TestResult] =
        for {
          q        <- Queue.unbounded[F, Any, String]
          q1       = q.contramap((i: Int) => i.toString)
          data     = Range.inclusive(0, 100)
          _        <- q1.offerAll(data)
          actual   <- q1.takeAll
          expected = data.map(_.toString)
        } yield assert(actual, equalTo(expected))

      def mapMQueueTest[F[+_]](implicit F: Effect[F], R: Runtime[ZEnv]): F[TestResult] =
        for {
          q        <- Queue.unbounded[F, Any, Int]
          q1       = q.mapM(i => F.pure(i.toString))
          data     = Range.inclusive(0, 100)
          _        <- q1.offerAll(data)
          actual   <- q1.takeAll
          expected = data.map(_.toString)
        } yield assert(actual, equalTo(expected))

      suite("catzQueueSpec")(
        testF("can use a bounded queue from Cats Effect IO") {
          implicit val r: zio.Runtime[zio.ZEnv] = new DefaultRuntime {}
          implicit val c: ContextShift[CIO]     = CIO.contextShift(global)
          boundedQueueTest[CIO]
        },
        testF("can use a dropping queue from Cats Effect IO") {
          implicit val r: zio.Runtime[zio.ZEnv] = new DefaultRuntime {}
          implicit val c: ContextShift[CIO]     = CIO.contextShift(global)
          droppingQueueTest[CIO]
        },
        testF("can use a sliding queue from Cats Effect IO") {
          implicit val r: zio.Runtime[zio.ZEnv] = new DefaultRuntime {}
          implicit val c: ContextShift[CIO]     = CIO.contextShift(global)
          slidingQueueTest[CIO]
        },
        testF("can use an unbounded queue from Cats Effect IO") {
          implicit val r: zio.Runtime[zio.ZEnv] = new DefaultRuntime {}
          implicit val c: ContextShift[CIO]     = CIO.contextShift(global)
          unboundedQueueTest[CIO]
        },
        testF("can contramap a queue from Cats Effect IO") {
          implicit val r: zio.Runtime[zio.ZEnv] = new DefaultRuntime {}
          implicit val c: ContextShift[CIO]     = CIO.contextShift(global)
          contramapQueueTest[CIO]
        },
        testF("can mapM a queue from Cats Effect IO") {
          implicit val r: zio.Runtime[zio.ZEnv] = new DefaultRuntime {}
          implicit val c: ContextShift[CIO]     = CIO.contextShift(global)
          mapMQueueTest[CIO]
        }
      )
    })
