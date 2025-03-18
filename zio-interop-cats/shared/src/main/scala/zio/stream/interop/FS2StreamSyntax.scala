package zio
package stream.interop

import cats.effect.Resource
import fs2.Stream
import zio.interop.catz.{ concurrentInstance, scopedSyntax }
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
  def toFs2Stream(implicit trace: Trace): fs2.Stream[ZIO[R, E, _], A] = {
    import zio.interop.catz.generic.*

    fs2.Stream.resource(Resource.scopedZIO[R, E, ZIO[R, Option[E], Chunk[A]]](stream.toPull)).flatMap { pull =>
      fs2.Stream.repeatEval(pull.unsome).unNoneTerminate.flatMap { chunk =>
        fs2.Stream.chunk(fs2.Chunk.from(chunk))
      }
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
  def toZStream[R1 <: R](queueSize: Int = 16)(implicit trace: Trace): ZStream[R1, Throwable, A] = {
    val useChunkedImpl = queueSize > 1
    val realQueueSize  = if (useChunkedImpl) queueSize else 1

    def streamToQueue(queue: Enqueue[Take[Throwable, A]]): Stream[RIO[R, _], Any] =
      if (useChunkedImpl) {
        stream
          .chunkLimit(queueSize)
          .evalTap(a => queue.offer(Take.chunk(zio.Chunk.fromIterator(a.iterator))))
      } else {
        stream.evalTap(a => queue.offer(Take.single(a)))
      }

    ZStream
      .scoped[R1] {
        for {
          queue <- ZIO.acquireRelease(Queue.bounded[Take[Throwable, A]](realQueueSize))(_.shutdown)
          _     <- streamToQueue(queue)
                     .compile[RIO[R, _], RIO[R, _], Any]
                     .drain
                     .onExit {
                       case Exit.Success(_)           => queue.offer(Take.end)
                       case failure @ Exit.Failure(_) => queue.offer(Take.done(failure))
                     }
                     .forkScoped
        } yield ZStream.fromQueue(queue).flattenTake
      }
      .flatten
  }

}
