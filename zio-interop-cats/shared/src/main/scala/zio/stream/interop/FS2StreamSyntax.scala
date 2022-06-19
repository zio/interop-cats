package zio
package stream.interop

import cats.effect.Resource
import fs2.{ Pull, Stream }
import zio.interop.catz.*
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
  def toFs2Stream: fs2.Stream[ZIO[R, E, _], A] = {
    import zio.interop.catz.generic.*

    val resource: Resource[ZIO[R, E, _], ZIO[R, Option[E], Chunk[A]]] = stream.process.toResourceZIO
    fs2.Stream.resource(resource).flatMap { pull =>
      fs2.Stream.repeatEval(pull.optional).unNoneTerminate.flatMap { chunk =>
        fs2.Stream.chunk(fs2.Chunk.indexedSeq(chunk))
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
  def toZStream[R1 <: R](queueSize: Int = 16): ZStream[R1, Throwable, A] =
    if (queueSize > 1) toZStreamChunk(queueSize) else toZStreamChunk(queueSize = 1)

  def toZStreamChunk[R1 <: R](queueSize: Int = 16): ZStream[R1, Throwable, A] = {
    def integrate(stream: Stream[RIO[R, _], A], zQueue: Queue[Take[Throwable, A]]): Pull[RIO[R, _], A, Unit] =
      stream.pull.uncons.flatMap {
        case None =>
          fs2.Pull.eval(zQueue.offer(Take.end)) >> fs2.Pull.done

        case Some((fs2Chunk: fs2.Chunk[A], stream)) =>
          val offer = zQueue.offer(Take.chunk(toZioChunk(fs2Chunk)))
          fs2.Pull.eval(offer) >> integrate(stream, zQueue)
      }

    ZStream.fromEffect(Queue.bounded[Take[Throwable, A]](queueSize)).flatMap { q =>
      val toQueue = ZStream.fromEffect {
        integrate(stream, q).stream
          .handleErrorWith(e => Stream.eval(q.offer(Take.fail(e))))
          .compile[RIO[R, _], RIO[R, _], Any]
          .drain
      }

      val fromQueue = ZStream.fromQueue(q).flattenTake
      fromQueue.drainFork(toQueue)
    }
  }

  private def toZioChunk(in: fs2.Chunk[A]): Chunk[A] =
    in match {
      case fs2.Chunk.ArraySlice(values, _, _) =>
        Chunk.fromArray(values)

      case singleton: fs2.Chunk.Singleton[a] =>
        Chunk.single(singleton.value)

      case other =>
        Chunk.fromIterator(other.iterator)
    }
}
