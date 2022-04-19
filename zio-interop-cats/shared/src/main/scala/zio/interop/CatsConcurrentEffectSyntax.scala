package zio.interop

import cats.effect.ConcurrentEffect
import zio.{ RIO, Runtime, ZIO, ZTraceElement }

import scala.language.implicitConversions

trait CatsConcurrentEffectSyntax {
  implicit final def ZIOConcurrentEffectOps(@deprecated("", "") zio: ZIO.type): CatsConcurrentEffectSyntax.zioOps.type =
    CatsConcurrentEffectSyntax.zioOps
}

private[interop] object CatsConcurrentEffectSyntax {
  object zioOps {
    final def concurrentEffect[R](implicit trace: ZTraceElement): ZIO[R, Nothing, ConcurrentEffect[RIO[R, *]]] =
      ZIO.runtime.map(catz.taskEffectInstance(_: Runtime[R]))
    final def concurrentEffectWith[R, E, A](
      f: ConcurrentEffect[RIO[R, *]] => ZIO[R, E, A]
    )(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.runtime.flatMap(f apply catz.taskEffectInstance(_: Runtime[R]))
  }
}
