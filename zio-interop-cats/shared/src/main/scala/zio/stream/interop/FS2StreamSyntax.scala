package zio
package stream.interop

import fs2.Stream
import zio.interop.catz._
import zio.stream.{ Take, ZStream }

import scala.language.implicitConversions

trait FS2StreamSyntax {

  implicit final def fs2RIOStreamSyntax[R, A](stream: Stream[RIO[R, *], A]): FS2RIOStreamSyntax[R, A] =
    new FS2RIOStreamSyntax(stream)

  implicit final def zStreamSyntax[R, E, A](stream: ZStream[R, E, A]): ZStreamSyntax[R, E, A] =
    new ZStreamSyntax(stream)
}

class ZStreamSyntax[R, E, A](private val stream: ZStream[R, E, A]) extends AnyVal {

  /** Convert a [[zio.stream.ZStream]] into an [[fs2.Stream]]. */
  def toFs2Stream(implicit trace: ZTraceElement): fs2.Stream[ZIO[R, E, *], A] =
    fs2.Stream.resource(stream.process.toResourceZIO).flatMap { pull =>
      fs2.Stream.repeatEval(pull.unsome).unNoneTerminate.flatMap { chunk =>
        fs2.Stream.chunk(fs2.Chunk.indexedSeq(chunk))
      }
    }
}

final class FS2RIOStreamSyntax[R, A](private val stream: Stream[RIO[R, *], A]) {

  /**
   * Convert an [[fs2.Stream]] into a [[zio.stream.ZStream]].
   * This method requires a non-empty queue.
   *
   * When `queueSize` >= 2 utilizes chunks for better performance.
   *
   * @note when possible use only power of 2 queue sizes; this will provide better performance of the queue.
   */
  def toZStream[R1 <: R](queueSize: Int = 16)(implicit trace: ZTraceElement): ZStream[R1, Throwable, A] =
    if (queueSize > 1) toZStreamChunk(queueSize) else toZStreamSingle

  private def toZStreamSingle[R1 <: R](implicit trace: ZTraceElement): ZStream[R1, Throwable, A] =
    ZStream.managed {
      for {
        queue <- Queue.bounded[Take[Throwable, A]](1).toManagedWith(_.shutdown)
        _ <- ZIO
              .runtime[R1]
              .toManaged
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

  private def toZStreamChunk[R1 <: R](queueSize: Int)(implicit trace: ZTraceElement): ZStream[R1, Throwable, A] =
    ZStream.managed {
      for {
        queue <- Queue.bounded[Take[Throwable, A]](queueSize).toManagedWith(_.shutdown)
        _ <- ZIO
              .runtime[R1]
              .toManaged
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
