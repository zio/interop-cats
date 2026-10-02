package zio.interop

import fs2.Stream
import zio.Random.nextIntBetween
import zio.interop.catz.*
import zio.stream.ZStream
import zio.test.*
import zio.test.Assertion.*
import zio.{ Chunk, Ref, Task }

object fs2StreamSpec extends ZIOSpecDefault {
  import zio.stream.interop.fs2z.*

  val exception: Throwable = new Exception("Failed")

  def fs2StreamFromChunk[A](chunk: Chunk[A]) =
    fs2.Stream.chunk[Task, A](fs2.Chunk.from(chunk))

  def assertEqual[A](actual: fs2.Stream[Task, A], expected: fs2.Stream[Task, A]) =
    for {
      x <- actual.compile.toVector
      y <- expected.compile.toVector
    } yield assert(x)(equalTo(y))

  def assertEqual[R, E, A](actual: ZStream[R, E, A], expected: ZStream[R, E, A]) =
    for {
      x <- actual.runCollect
      y <- expected.runCollect
    } yield assert(x)(equalTo(y))

  def spec: Spec[Any, Throwable] = suite("zio.stream.ZStream <-> fs2.Stream")(
    suite("test toFs2Stream conversion")(
      test("simple stream")(check(Gen.chunkOf(Gen.int)) { (chunk: Chunk[Int]) =>
        assertEqual(ZStream.fromChunk(chunk).toFs2Stream, fs2StreamFromChunk(chunk))
      }),
      test("non empty stream")(check(Gen.chunkOf1(Gen.long)) { chunk =>
        assertEqual(ZStream.fromChunk(chunk).toFs2Stream, fs2StreamFromChunk(chunk))
      }),
      test("100 element stream")(check(Gen.chunkOfN(100)(Gen.long)) { chunk =>
        assertEqual(ZStream.fromChunk(chunk).toFs2Stream, fs2StreamFromChunk(chunk))
      }),
      test("error propagation") {
        val result = ZStream.fail(exception).toFs2Stream.compile.drain.exit
        assertZIO(result)(fails(equalTo(exception)))
      },
      test("unguarded throw propagation") {
        val stream: ZStream[Any, Throwable, Int] = ZStream(1, 2, 3) ++ (throw exception)
        val result                               = stream.toFs2Stream.compile.drain.exit
        assertZIO(result)(dies(equalTo(exception)))
      }
    ),
    suite("test toZStream conversion")(
      test("simple stream")(check(Gen.chunkOf(Gen.int)) { (chunk: Chunk[Int]) =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(), ZStream.fromChunk(chunk))
      }),
      test("non empty stream")(check(Gen.chunkOf1(Gen.long)) { chunk =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(), ZStream.fromChunk(chunk))
      }),
      test("100 element stream")(check(Gen.chunkOfN(100)(Gen.long)) { chunk =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(), ZStream.fromChunk(chunk))
      }),
      test("error propagation") {
        val result = Stream.raiseError[Task](exception).toZStream().runDrain.exit
        assertZIO(result)(fails(equalTo(exception)))
      },
      test("unguarded throw propagation") {
        val result = (Stream[Task, Int](1, 2, 3) ++ (throw exception)).toZStream().runDrain.exit
        assertZIO(result)(dies(equalTo(exception)))
      },
      test("releases all resources by the time the failover stream has started") {
        for {
          queueSize <- nextIntBetween(2, 32)
          fins      <- Ref.make(Chunk[Int]())
          stream     = Stream(1).onFinalize(fins.update(1 +: _)) >>
                         Stream(2).onFinalize(fins.update(2 +: _)) >>
                         Stream(3).onFinalize(fins.update(3 +: _)) >>
                         Stream.raiseError[Task](exception)
          result    <- stream.toZStream(queueSize).drain.catchAllCause(_ => ZStream.fromZIO(fins.get)).runCollect
        } yield assert(result.flatten)(equalTo(Chunk(1, 2, 3)))
      },
      test("bigger queueSize than a chunk size")(check(Gen.chunkOfN(10)(Gen.long)) { chunk =>
        for {
          queueSize <- nextIntBetween(32, 256)
          result    <- assertEqual(fs2StreamFromChunk(chunk).toZStream(queueSize), ZStream.fromChunk(chunk))
        } yield result
      }),
      test("queueSize == 1")(check(Gen.chunkOfN(10)(Gen.long)) { chunk =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(1), ZStream.fromChunk(chunk))
      }),
      test("negative queueSize")(check(Gen.chunkOfN(10)(Gen.long)) { chunk =>
        for {
          queueSize <- nextIntBetween(-128, 0)
          result    <- assertEqual(fs2StreamFromChunk(chunk).toZStream(queueSize), ZStream.fromChunk(chunk))
        } yield result
      }),
      test("RIO")(check(Gen.chunkOfN(10)(Gen.long)) { chunk =>
        for {
          queueSize <- nextIntBetween(2, 128)
          result    <- assertEqual(
                         fs2StreamFromChunk(chunk).covary[Task].toZStream(queueSize),
                         ZStream.fromChunk(chunk)
                       )
        } yield result
      }),
      test("regression https://github.com/zio/interop-cats/issues/709") {
        val stream = fs2.Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).toZStream(1)
        for {
          result <- stream.take(2).runSum
        } yield assert(result)(equalTo(3))
      },
      test("regression https://github.com/zio/interop-cats/issues/709 #2") {
        val stream = fs2.Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).toZStream(2)
        for {
          result <- stream.take(2).runSum
        } yield assert(result)(equalTo(3))
      },
      test("regression https://github.com/zio/interop-cats/issues/709 #3") {
        val stream = fs2.Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).toZStream(9)
        for {
          result <- stream.take(2).runSum
        } yield assert(result)(equalTo(3))
      }
    )
  )
}
