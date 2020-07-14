package zio
package stream.interop

import fs2.Stream
import zio.stream.{ Take, ZStream }
import zio.interop.catz._

trait FS2StreamSyntax {

  import scala.language.implicitConversions

  implicit final def fs2RIOStreamSyntax[R, A](stream: Stream[RIO[R, *], A]): FS2RIOStreamSyntax[R, A] =
    new FS2RIOStreamSyntax(stream)
}

final class FS2RIOStreamSyntax[R, A](private val stream: Stream[RIO[R, *], A]) {

  /**
   * Convert a fs2.Stream into a ZStream.
   * This method requires non-empty queue.
   *
   * When `queueSize` >= 2 utilizes chunks for better performance.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance of the queue.
   */
  def toZStream[R1 <: R](queueSize: Int = 16): ZStream[R1, Throwable, A] =
    if (queueSize > 1)
      toZStreamChunk(queueSize)
    else
      toZStreamSingle

  private def toZStreamSingle[R1 <: R]: ZStream[R1, Throwable, A] =
    ZStream.managed {
      for {
        queue <- Queue.bounded[Take[Throwable, A]](1).toManaged(_.shutdown)
        _ <- ZIO
              .runtime[R1]
              .toManaged_
              .flatMap { implicit runtime =>
                (stream.evalTap(a => queue.offer(Take.single(a))) ++ fs2.Stream
                  .eval(queue.offer(Take.end)))
                  .handleErrorWith(e => fs2.Stream.eval(queue.offer(Take.fail(e))).drain)
                  .compile
                  .resource
                  .drain
                  .toManaged
              }
              .fork
      } yield ZStream.fromQueue(queue).flattenTake
    }.flatten

  private def toZStreamChunk[R1 <: R](queueSize: Int): ZStream[R1, Throwable, A] =
    ZStream.managed {
      for {
        queue <- Queue.bounded[Take[Throwable, A]](queueSize).toManaged(_.shutdown)
        _ <- ZIO
              .runtime[R1]
              .toManaged_
              .flatMap { implicit runtime =>
                (stream
                  .chunkLimit(queueSize)
                  .evalTap(a => queue.offer(Take.chunk(zio.Chunk.fromIterable(a.toList))))
                  .unchunk ++ fs2.Stream
                  .eval(queue.offer(Take.end)))
                  .handleErrorWith(e => fs2.Stream.eval(queue.offer(Take.fail(e))).drain)
                  .compile
                  .resource
                  .drain
                  .toManaged
              }
              .fork
      } yield ZStream.fromQueue(queue).flattenTake
    }.flatten
}
