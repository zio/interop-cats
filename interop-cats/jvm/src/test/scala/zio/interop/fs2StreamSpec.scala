package zio.interop

import fs2.Stream
import zio.clock.Clock
import zio.{ Chunk, RIO, Ref, Task }
import zio.stream.ZStream
import zio.test.Assertion.{ equalTo, fails }
import zio.test._
import zio.interop.catz._
import zio.random.nextIntBetween

object fs2StreamSpec extends DefaultRunnableSpec {
  import zio.stream.interop.fs2z._

  val exception: Throwable = new Exception("Failed")

  def fs2StreamFromChunk[A](chunk: Chunk[A]) =
    fs2.Stream.chunk[Task, A](fs2.Chunk.indexedSeq(chunk))

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

  def spec = suite("zio.stream.ZStream <-> fs2.Stream")(
    suite("test toFs2Stream conversion")(
      testM("simple stream")(checkM(Gen.chunkOf(Gen.anyInt)) { chunk: Chunk[Int] =>
        assertEqual(ZStream.fromChunk(chunk).toFs2Stream, fs2StreamFromChunk(chunk))
      }),
      testM("non empty stream")(checkM(Gen.chunkOf1(Gen.anyLong)) { chunk =>
        assertEqual(ZStream.fromChunk(chunk).toFs2Stream, fs2StreamFromChunk(chunk))
      }),
      testM("100 element stream")(checkM(Gen.chunkOfN(100)(Gen.anyLong)) { chunk =>
        assertEqual(ZStream.fromChunk(chunk).toFs2Stream, fs2StreamFromChunk(chunk))
      }),
      testM("error propagation") {
        Task.concurrentEffectWith { implicit CE =>
          val result = ZStream.fail(exception).toFs2Stream.compile.drain.run
          assertM(result)(fails(equalTo(exception)))
        }
      }
    ),
    suite("test toZStream conversion")(
      testM("simple stream")(checkM(Gen.chunkOf(Gen.anyInt)) { chunk: Chunk[Int] =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(), ZStream.fromChunk(chunk))
      }),
      testM("non empty stream")(checkM(Gen.chunkOf1(Gen.anyLong)) { chunk =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(), ZStream.fromChunk(chunk))
      }),
      testM("100 element stream")(checkM(Gen.chunkOfN(100)(Gen.anyLong)) { chunk =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(), ZStream.fromChunk(chunk))
      }),
      testM("error propagation") {
        Task.concurrentEffectWith { implicit CE =>
          val result = Stream.raiseError[Task](exception).toZStream().runDrain.run
          assertM(result)(fails(equalTo(exception)))
        }
      },
      testM("releases all resources by the time the failover stream has started") {
        for {
          queueSize <- nextIntBetween(2, 32)
          fins      <- Ref.make(Chunk[Int]())
          stream = Stream(1).onFinalize(fins.update(1 +: _)) >>
            Stream(2).onFinalize(fins.update(2 +: _)) >>
            Stream(3).onFinalize(fins.update(3 +: _)) >>
            Stream.raiseError[Task](exception)
          result <- stream.toZStream(queueSize).drain.catchAllCause(_ => ZStream.fromEffect(fins.get)).runCollect
        } yield assert(result.flatten)(equalTo(Chunk(1, 2, 3)))
      },
      testM("bigger queueSize than a chunk size")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
        for {
          queueSize <- nextIntBetween(32, 256)
          result    <- assertEqual(fs2StreamFromChunk(chunk).toZStream(queueSize), ZStream.fromChunk(chunk))
        } yield result
      }),
      testM("queueSize == 1")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
        assertEqual(fs2StreamFromChunk(chunk).toZStream(1), ZStream.fromChunk(chunk))
      }),
      testM("negative queueSize")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
        for {
          queueSize <- nextIntBetween(-128, 0)
          result    <- assertEqual(fs2StreamFromChunk(chunk).toZStream(queueSize), ZStream.fromChunk(chunk))
        } yield result
      }),
      testM("RIO")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
        for {
          queueSize <- nextIntBetween(2, 128)
          result <- assertEqual(
                     fs2StreamFromChunk(chunk).covary[RIO[Clock, *]].toZStream(queueSize),
                     ZStream.fromChunk(chunk)
                   )
        } yield result
      })
    )
  )
}
