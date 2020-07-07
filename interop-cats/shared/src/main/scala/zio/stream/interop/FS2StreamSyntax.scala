package zio
package stream.interop

import fs2.Stream
import zio.stream.{ Take, ZStream }
import zio.interop.catz._

trait FS2StreamSyntax {

  import scala.language.implicitConversions

  implicit final def fs2TaskStreamSyntax[R, E <: Throwable, A](stream: Stream[Task, A]): FS2TaskStreamSyntax[R, E, A] =
    new FS2TaskStreamSyntax(stream)
}

final class FS2TaskStreamSyntax[R, E <: Throwable, A](private val stream: Stream[Task, A]) {

  /**
   * Convert a fs2.Stream into a ZStream.
   * This method requires non-empty queue.
   *
   * When `queueSize` >= 2 utilizes chunks for better performance.
   *
   * @note when possible use only power of 2 capacities; this will
   * provide better performance of the queue.
   */
  def toZStream(queueSize: Int = 16): ZStream[R, Throwable, A] =
    if (queueSize > 1)
      toZStreamChunk(queueSize)
    else
      toZStreamSingle

  private def toZStreamSingle: ZStream[R, Throwable, A] =
    ZStream.managed {
      for {
        queue <- Queue.bounded[Take[Throwable, A]](1).toManaged(_.shutdown)
        _ <- ZIO
              .runtime[R]
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

  private def toZStreamChunk(queueSize: Int): ZStream[R, Throwable, A] =
    ZStream.managed {
      for {
        queue <- Queue.bounded[Take[Throwable, A]](queueSize).toManaged(_.shutdown)
        _ <- ZIO
              .runtime[R]
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
