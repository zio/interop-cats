package zio.interop.test

import cats.data.NonEmptyList
import zio.*
import zio.interop.catz.core.*
import zio.test.*

object NonEmptyListSyntaxSpec extends ZIOSpecDefault {
  private val nel = NonEmptyList.of(1, 2, 3, 4, 5)

  override def spec: Spec[Any, Any] =
    suite("ZIO.foreach and friends for NonEmptyList")(
      test("foreachNel preserves order and runs sequentially") {
        for {
          seen   <- Ref.make(List.empty[Int])
          result <- ZIO.foreachNel(nel)(i => seen.update(i :: _).as(i * 2))
          order  <- seen.get
        } yield assertTrue(result == NonEmptyList.of(2, 4, 6, 8, 10), order.reverse == nel.toList)
      },
      test("foreachParNel preserves order and runs in parallel") {
        // each element waits until all elements have started, which only terminates if they run in parallel
        for {
          started    <- Ref.make(0)
          allStarted <- Promise.make[Nothing, Unit]
          result     <- ZIO.foreachParNel(nel) { i =>
                          started.updateAndGet(_ + 1).flatMap(n => allStarted.succeed(()).when(n == nel.size)) *>
                            allStarted.await.as(i * 2)
                        }
        } yield assertTrue(result == NonEmptyList.of(2, 4, 6, 8, 10))
      } @@ TestAspect.timeout(30.seconds),
      test("collectAllNel and collectAllParNel") {
        val effects = nel.map(i => ZIO.succeed(i + 1))
        for {
          sequential <- ZIO.collectAllNel(effects)
          parallel   <- ZIO.collectAllParNel(effects)
        } yield assertTrue(sequential == NonEmptyList.of(2, 3, 4, 5, 6), parallel == sequential)
      },
      test("a single-element list") {
        ZIO.foreachNel(NonEmptyList.one("a"))(s => ZIO.succeed(s + "b")).map(r => assertTrue(r == NonEmptyList.one("ab")))
      },
      test("the first failure short-circuits foreachNel") {
        for {
          seen   <- Ref.make(List.empty[Int])
          result <- ZIO.foreachNel(nel)(i => seen.update(i :: _) *> ZIO.when(i == 2)(ZIO.fail("boom"))).either
          order  <- seen.get
        } yield assertTrue(result == Left("boom"), order.reverse == List(1, 2))
      },
      test("NonEmptyList.foreach preserves order and runs sequentially") {
        for {
          seen   <- Ref.make(List.empty[Int])
          result <- nel.foreach(i => seen.update(i :: _).as(i * 2))
          order  <- seen.get
        } yield assertTrue(result == NonEmptyList.of(2, 4, 6, 8, 10), order.reverse == nel.toList)
      },
      test("NonEmptyList.foreachPar preserves order and runs in parallel") {
        for {
          started    <- Ref.make(0)
          allStarted <- Promise.make[Nothing, Unit]
          result     <- nel.foreachPar { i =>
                          started.updateAndGet(_ + 1).flatMap(n => allStarted.succeed(()).when(n == nel.size)) *>
                            allStarted.await.as(i * 2)
                        }
        } yield assertTrue(result == NonEmptyList.of(2, 4, 6, 8, 10))
      } @@ TestAspect.timeout(30.seconds),
      test("NonEmptyList.collectAll and collectAllPar") {
        val effects = nel.map(i => ZIO.succeed(i + 1))
        for {
          sequential <- effects.collectAll
          parallel   <- effects.collectAllPar
        } yield assertTrue(sequential == NonEmptyList.of(2, 3, 4, 5, 6), parallel == sequential)
      },
      test("the first failure short-circuits NonEmptyList.foreach") {
        for {
          seen   <- Ref.make(List.empty[Int])
          result <- nel.foreach(i => seen.update(i :: _) *> ZIO.when(i == 2)(ZIO.fail("boom"))).either
          order  <- seen.get
        } yield assertTrue(result == Left("boom"), order.reverse == List(1, 2))
      },
      test("the two call styles") {
        val fromZio    = ZIO.foreachNel(NonEmptyList.of(1, 2, 3))(i => ZIO.succeed(i * 2))
        val fromNel    = NonEmptyList.of(1, 2, 3).foreach(i => ZIO.succeed(i * 2))
        val fromNelPar = NonEmptyList.of(1, 2, 3).foreachPar(i => ZIO.succeed(i * 2))
        for {
          a <- fromZio
          b <- fromNel
          c <- fromNelPar
        } yield assertTrue(a == NonEmptyList.of(2, 4, 6), b == a, c == a)
      }
    )
}
