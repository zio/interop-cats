package zio
package interop

import cats.effect.Sync
import cats.effect.kernel.Async
import fs2.Stream
import zio.interop.catz._
import zio.test.Assertion.equalTo
import zio.test._
import zio.test.interop.catz.test._

object Fs2ZioSpec extends CatsRunnableSpec {
  def spec =
    suite("ZIO with Fs2")(
      suite("fs2 parJoin")(
        testF("works if F is cats.effect.IO") {
          testCaseJoin[cats.effect.IO].map { ints =>
            assert(ints)(equalTo(List(1, 1)))
          }
        },
        testM("works if F is zio.interop.Task") {
          testCaseJoin[zio.RIO[ZEnv, *]].map { ints =>
            assert(ints)(equalTo(List(1, 1)))
          }
        }
      ),
      suite("fs2 resource handling")(
        testM("works when fiber is failed") {
          bracketFail
        },
        testM("work when fiber is terminated") {
          bracketTerminate
        },
        testM("work when fiber is interrupted") {
          bracketInterrupt
        }
      )
    )

  def bracketFail: ZIO[Any, Nothing, TestResult] =
    for {
      started  <- Promise.make[Nothing, Unit]
      released <- Promise.make[Nothing, Unit]
      fail     <- Promise.make[Nothing, Unit]
      _ <- Stream
            .bracket(started.succeed(()).unit)(_ => released.succeed(()).unit)
            .evalMap[Task, Unit](_ => fail.await *> IO.fail(new Exception()))
            .compile[Task, Task, Unit]
            .drain
            .fork
      _ <- started.await
      _ <- fail.succeed(())
      _ <- released.await
    } yield assertCompletes

  def bracketTerminate: ZIO[Any, Nothing, TestResult] =
    for {
      started   <- Promise.make[Nothing, Unit]
      released  <- Promise.make[Nothing, Unit]
      terminate <- Promise.make[Nothing, Unit]
      _ <- Stream
            .bracket(started.succeed(()).unit)(_ => released.succeed(()).unit)
            .evalMap[Task, Unit](_ => terminate.await *> IO.die(new Exception()))
            .compile[Task, Task, Unit]
            .drain
            .fork
      _ <- started.await
      _ <- terminate.succeed(())
      _ <- released.await
    } yield assertCompletes

  def bracketInterrupt: ZIO[Any, Nothing, TestResult] =
    for {
      started  <- Promise.make[Nothing, Unit]
      released <- Promise.make[Nothing, Unit]
      f <- Stream
            .bracket(IO.unit)(_ => released.succeed(()).unit)
            .evalMap[Task, Unit](_ => started.succeed(()) *> IO.never)
            .compile[Task, Task, Unit]
            .drain
            .fork
      _ <- started.await
      _ <- f.interrupt
      _ <- released.await
    } yield assertCompletes

  def testCaseJoin[F[_]: Async]: F[List[Int]] = {
    def one: F[Int]                   = Sync[F].delay(1)
    val s: Stream[F, Int]             = Stream.eval(one)
    val ss: Stream[F, Stream[F, Int]] = Stream.emits(List(s, s))
    ss.parJoin(2).compile.toList
  }
}
