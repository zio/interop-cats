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

  val exception = new Exception("Failed")

  def spec = suite("test toZStream conversion")(
    testM("simple stream")(checkM(Gen.chunkOf(Gen.anyInt)) { chunk: Chunk[Int] =>
      for {
        fs2Stream <- Stream
                      .fromIterator[Task](chunk.iterator)
                      .toZStream()
                      .runCollect
        zioStream <- ZStream.fromChunk(chunk).runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    }),
    testM("non empty stream")(checkM(Gen.chunkOf1(Gen.anyLong)) { chunk =>
      for {
        fs2Stream <- Stream
                      .fromIterator[Task](chunk.iterator)
                      .toZStream()
                      .runCollect
        zioStream <- ZStream.fromChunk(chunk).runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    }),
    testM("100 element stream")(checkM(Gen.chunkOfN(100)(Gen.anyLong)) { chunk =>
      for {
        fs2Stream <- Stream
                      .fromIterator[Task](chunk.iterator)
                      .toZStream()
                      .runCollect
        zioStream <- ZStream.fromChunk(chunk).runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    }),
    testM("error propagation") {
      Task.concurrentEffectWith { implicit CE =>
        val fs2Stream = Stream
          .raiseError[Task](exception)
          .toZStream()
          .runCollect
          .run
        assertM(fs2Stream)(fails(equalTo(exception)))
      }
    },
    testM("releases all resources by the time the failover stream has started") {
      for {
        queueSize <- nextIntBetween(2, 32)
        fins      <- Ref.make(Chunk[Int]())
        s = Stream(1).onFinalize(fins.update(1 +: _)) >>
          Stream(2).onFinalize(fins.update(2 +: _)) >>
          Stream(3).onFinalize(fins.update(3 +: _)) >>
          Stream.raiseError[Task](exception)

        result <- s.toZStream(queueSize).drain.catchAllCause(_ => ZStream.fromEffect(fins.get)).runCollect
      } yield assert(result.flatten)(equalTo(Chunk(1, 2, 3)))
    },
    testM("bigger queueSize than a chunk size")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
      for {
        queueSize <- nextIntBetween(32, 256)
        fs2Stream <- Stream
                      .fromIterator[Task](chunk.iterator)
                      .toZStream(queueSize)
                      .runCollect
        zioStream <- ZStream.fromChunk(chunk).runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    }),
    testM("queueSize == 1")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
      for {
        fs2Stream <- Stream
                      .fromIterator[Task](chunk.iterator)
                      .toZStream(1)
                      .runCollect
        zioStream <- ZStream.fromChunk(chunk).runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    }),
    testM("negative queueSize")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
      for {
        queueSize <- nextIntBetween(-128, 0)
        fs2Stream <- Stream
                      .fromIterator[Task](chunk.iterator)
                      .toZStream(queueSize)
                      .runCollect
        zioStream <- ZStream.fromChunk(chunk).runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    }),
    testM("RIO")(checkM(Gen.chunkOfN(10)(Gen.anyLong)) { chunk =>
      for {
        zioStream <- ZStream.fromChunk(chunk).runCollect
        queueSize <- nextIntBetween(2, 128)
        fs2Stream <- Stream
                      .fromIterator[RIO[Clock, *]](chunk.iterator)
                      .toZStream(queueSize)
                      .runCollect
      } yield assert(fs2Stream)(equalTo(zioStream))
    })
  )
}
