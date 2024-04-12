package zio
package interop

import cats.effect.kernel.{ Async, Sync }
import cats.effect.IO as CIO
import fs2.Stream
import zio.interop.catz.*
import zio.test.Assertion.equalTo
import zio.test.*
import zio.test.interop.catz.test.*

object Fs2ZioSpec extends CatsRunnableSpec {
  def spec: Spec[Any, Throwable] =
    suite("ZIO with Fs2")(
      suite("fs2 parJoin")(
        testF("works if F is cats.effect.IO") {
          testCaseJoin[CIO].map { ints =>
            assert(ints)(equalTo(List(1, 1)))
          }
        },
        test("works if F is zio.interop.Task") {
          testCaseJoin[Task].map { ints =>
            assert(ints)(equalTo(List(1, 1)))
          }
        }
      ),
      suite("fs2 resource handling")(
        test("works when fiber is failed") {
          bracketFail
        },
        test("work when fiber is terminated") {
          bracketTerminate
        },
        test("work when fiber is interrupted") {
          bracketInterrupt
        }
      )
    )

  def bracketFail: UIO[TestResult] =
    for {
      started  <- Promise.make[Nothing, Unit]
      released <- Promise.make[Nothing, Unit]
      fail     <- Promise.make[Nothing, Unit]
      _        <- Stream
                    .bracket(started.succeed(()).unit)(_ => released.succeed(()).unit)
                    .evalMap[Task, Unit](_ => fail.await *> ZIO.fail(new Exception()))
                    .compile[Task, Task, Unit]
                    .drain
                    .fork
      _        <- started.await
      _        <- fail.succeed(())
      _        <- released.await
    } yield assertCompletes

  def bracketTerminate: UIO[TestResult] =
    for {
      started   <- Promise.make[Nothing, Unit]
      released  <- Promise.make[Nothing, Unit]
      terminate <- Promise.make[Nothing, Unit]
      _         <- Stream
                     .bracket(started.succeed(()).unit)(_ => released.succeed(()).unit)
                     .evalMap[Task, Unit](_ => terminate.await *> ZIO.die(new Exception()))
                     .compile[Task, Task, Unit]
                     .drain
                     .fork
      _         <- started.await
      _         <- terminate.succeed(())
      _         <- released.await
    } yield assertCompletes

  def bracketInterrupt: UIO[TestResult] =
    for {
      started  <- Promise.make[Nothing, Unit]
      released <- Promise.make[Nothing, Unit]
      f        <- Stream
                    .bracket(ZIO.unit)(_ => released.succeed(()).unit)
                    .evalMap[Task, Unit](_ => started.succeed(()) *> ZIO.never)
                    .compile[Task, Task, Unit]
                    .drain
                    .fork
      _        <- started.await
      _        <- f.interrupt
      _        <- released.await
    } yield assertCompletes

  def testCaseJoin[F[_]: Async]: F[List[Int]] = {
    def one: F[Int]                   = Sync[F].delay(1)
    val s: Stream[F, Int]             = Stream.eval(one)
    val ss: Stream[F, Stream[F, Int]] = Stream.emits(List(s, s))
    ss.parJoin(2).compile.toList
  }
}
