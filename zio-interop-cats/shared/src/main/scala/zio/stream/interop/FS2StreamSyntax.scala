package zio
package stream.interop

import cats.effect.Resource
import fs2.Stream
import zio.interop.catz.{ concurrentInstance, scopedSyntax, zioResourceSyntax }
import zio.stream.{ Take, ZStream }

import scala.language.implicitConversions

trait FS2StreamSyntax {

  implicit final def fs2RIOStreamSyntax[R, A](stream: Stream[RIO[R, _], A]): FS2RIOStreamSyntax[R, A] =
    new FS2RIOStreamSyntax(stream)

  implicit final def zStreamSyntax[R, E, A](stream: ZStream[R, E, A]): ZStreamSyntax[R, E, A] =
    new ZStreamSyntax(stream)
}

class ZStreamSyntax[R, E, A](private val stream: ZStream[R, E, A]) extends AnyVal {

  /** Convert a [[zio.stream.ZStream]] into an [[fs2.Stream]]. */
  def toFs2Stream(implicit trace: Trace): fs2.Stream[ZIO[R, E, _], A] =
    fs2.Stream.resource(Resource.scopedZIO[R, E, ZIO[R, Option[E], Chunk[A]]](stream.toPull)).flatMap { pull =>
      fs2.Stream.repeatEval(pull.unsome).unNoneTerminate.flatMap { chunk =>
        fs2.Stream.chunk(fs2.Chunk.indexedSeq(chunk))
      }
    }
}

final class FS2RIOStreamSyntax[R, A](private val stream: Stream[RIO[R, _], A]) {

  /**
   * Convert an [[fs2.Stream]] into a [[zio.stream.ZStream]].
   * This method requires a non-empty queue.
   *
   * When `queueSize` >= 2 utilizes chunks for better performance.
   *
   * @note when possible use only power of 2 queue sizes; this will provide better performance of the queue.
   */
  def toZStream[R1 <: R](queueSize: Int = 16)(implicit trace: Trace): ZStream[R1, Throwable, A] =
    if (queueSize > 1) toZStreamChunk(queueSize) else toZStreamSingle

  private def toZStreamSingle[R1 <: R](implicit trace: Trace): ZStream[R1, Throwable, A] =
    ZStream
      .scoped[R1] {
        for {
          queue <- ZIO.acquireRelease(Queue.bounded[Take[Throwable, A]](1))(_.shutdown)
          _     <-
            (stream.evalTap(a => queue.offer(Take.single(a))) ++ fs2.Stream
              .eval(queue.offer(Take.end)))
              .handleErrorWith(e => fs2.Stream.eval(queue.offer(Take.fail(e))).drain)
              .compile[RIO[R, _], RIO[R, _], Any]
              .resource
              .drain
              .toScopedZIO
              .forkScoped
        } yield ZStream.fromQueue(queue).flattenTake
      }
      .flatten

  private def toZStreamChunk[R1 <: R](queueSize: Int)(implicit trace: Trace): ZStream[R1, Throwable, A] =
    ZStream
      .scoped[R1] {
        for {
          queue <- ZIO.acquireRelease(Queue.bounded[Take[Throwable, A]](queueSize))(_.shutdown)
          _     <- {
            stream
              .chunkLimit(queueSize)
              .evalTap(a => queue.offer(Take.chunk(zio.Chunk.fromIterable(a.toList))))
              .unchunk ++ fs2.Stream.eval(queue.offer(Take.end))
          }.handleErrorWith(e => fs2.Stream.eval(queue.offer(Take.fail(e))).drain)
            .compile[RIO[R, _], RIO[R, _], Any]
            .resource
            .drain
            .toScopedZIO
            .forkScoped
        } yield ZStream.fromQueue(queue).flattenTake
      }
      .flatten
}
