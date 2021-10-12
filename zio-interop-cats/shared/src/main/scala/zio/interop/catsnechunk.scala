/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.interop

import cats.*
import cats.data.{ Ior, NonEmptyList }
import cats.syntax.functor.*
import zio.{ Chunk, ChunkBuilder, NonEmptyChunk }

import scala.annotation.tailrec

/**
 * The same instances for [[NonEmptyChunk]] that Cats defines for [[cats.data.NonEmptyVector]].
 */
trait CatsNonEmptyChunkInstances extends CatsNonEmptyChunkInstances1 {

  implicit def nonEmptyChunkSemigroup[A]: Semigroup[NonEmptyChunk[A]] =
    new NonEmptyChunkSemigroup[A]

  implicit def nonEmptyChunkOrder[A: Order]: Order[NonEmptyChunk[A]] =
    Order.by((a: NonEmptyChunk[A]) => a.toChunk)(zio.interop.catz.core.chunkOrder)

  implicit def nonEmptyChunkPartialOrder[A: PartialOrder]: PartialOrder[NonEmptyChunk[A]] =
    PartialOrder.by((a: NonEmptyChunk[A]) => a.toChunk)(zio.interop.catz.core.chunkPartialOrder)

  /* @see [[cats.data.NonEmptyVectorInstances.catsDataInstancesForNonEmptyVector]] */
  implicit val nonEmptyChunkStdInstances
    : SemigroupK[NonEmptyChunk] & Bimonad[NonEmptyChunk] & NonEmptyTraverse[NonEmptyChunk] & Align[NonEmptyChunk] =
    new SemigroupK[NonEmptyChunk]
      with Bimonad[NonEmptyChunk]
      with NonEmptyTraverse[NonEmptyChunk]
      with Align[NonEmptyChunk] {

      private def ChunkInstances =
        zio.interop.catz.core.chunkStdInstances

      // Functor
      override def map[A, B](fa: NonEmptyChunk[A])(f: A => B): NonEmptyChunk[B] =
        fa.map(f)

      // Applicative
      override def pure[A](x: A): NonEmptyChunk[A] =
        NonEmptyChunk.single(x)

      // FlatMap
      override def flatMap[A, B](fa: NonEmptyChunk[A])(f: A => NonEmptyChunk[B]): NonEmptyChunk[B] =
        fa.flatMap(f)

      override def tailRecM[A, B](a: A)(f: A => NonEmptyChunk[Either[A, B]]): NonEmptyChunk[B] =
        NonEmptyChunk.nonEmpty(ChunkInstances.tailRecM(a)(f(_).toChunk))

      // CoflatMap
      override def coflatMap[A, B](fa: NonEmptyChunk[A])(f: NonEmptyChunk[A] => B): NonEmptyChunk[B] = {
        @tailrec def loop(chunk: Chunk[A], b: ChunkBuilder[B]): Chunk[B] = chunk match {
          case a +: as => loop(as, b += f(NonEmptyChunk.fromIterable(a, as)))
          case _       => b.result()
        }
        val tail                                                         = fa.tail
        NonEmptyChunk.fromIterable(f(fa), loop(tail, ChunkBuilder.make[B](tail.size)))
      }

      // Comonad
      override def extract[A](fa: NonEmptyChunk[A]): A =
        fa.head

      // NonEmptyTraverse
      override def nonEmptyTraverse[G[_], A, B](
        nec: NonEmptyChunk[A]
      )(f: A => G[B])(implicit G: Apply[G]): G[NonEmptyChunk[B]] = {
        def loop(head: A, tail: Chunk[A]): Eval[G[NonEmptyChunk[B]]] =
          tail.headOption match {
            case None    =>
              Eval.now(f(head).map(NonEmptyChunk.single))
            case Some(h) =>
              G.map2Eval(f(head), Eval.defer(loop(h, tail.tail)))((b, acc) => acc.prepend(Chunk.single(b)))
          }

        loop(nec.head, nec.tail).value
      }

      // Traverse
      override def traverse[G[_], A, B](
        fa: NonEmptyChunk[A]
      )(f: A => G[B])(implicit G: Applicative[G]): G[NonEmptyChunk[B]] = {
        val traverseTail = Eval.always(ChunkInstances.traverse(fa.tail)(f))
        G.map2Eval(f(fa.head), traverseTail)(NonEmptyChunk.fromIterable(_, _)).value
      }

      // Reducible
      override def reduce[A](fa: NonEmptyChunk[A])(implicit A: Semigroup[A]): A =
        fa.reduce(A.combine)

      override def reduceLeft[A](fa: NonEmptyChunk[A])(f: (A, A) => A): A =
        fa.reduceLeft(f)

      override def reduceLeftTo[A, B](fa: NonEmptyChunk[A])(f: A => B)(g: (B, A) => B): B =
        fa.reduceMapLeft(f)(g)

      override def reduceRightTo[A, B](fa: NonEmptyChunk[A])(f: A => B)(g: (A, Eval[B]) => Eval[B]): Eval[B] = {
        val lastIndex             = fa.length - 1
        def loop(i: Int): Eval[B] =
          if (i < lastIndex) g(fa(i), Eval.defer(loop(i + 1)))
          else Eval.later(f(fa(lastIndex)))
        Eval.defer(loop(0))
      }

      override def toNonEmptyList[A](fa: NonEmptyChunk[A]): NonEmptyList[A] =
        fa.toCons match { case a :: as => NonEmptyList(a, as) }

      // Foldable
      override def foldLeft[A, B](fa: NonEmptyChunk[A], b: B)(f: (B, A) => B): B =
        fa.foldLeft(b)(f)

      override def foldRight[A, B](fa: NonEmptyChunk[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
        ChunkInstances.foldRight(fa.toChunk, lb)(f)

      override def reduceLeftOption[A](fa: NonEmptyChunk[A])(f: (A, A) => A): Option[A] =
        fa.reduceLeftOption(f)

      override def get[A](fa: NonEmptyChunk[A])(idx: Long): Option[A] =
        ChunkInstances.get(fa.toChunk)(idx)

      override def collectFirst[A, B](fa: NonEmptyChunk[A])(pf: PartialFunction[A, B]): Option[B] =
        fa.collectFirst(pf)

      override def collectFirstSome[A, B](fa: NonEmptyChunk[A])(f: A => Option[B]): Option[B] =
        fa.collectFirst(Function.unlift(f))

      override def fold[A](fa: NonEmptyChunk[A])(implicit A: Monoid[A]): A =
        fa.reduce(A.combine)

      override def foldMap[A, B](fa: NonEmptyChunk[A])(f: A => B)(implicit B: Monoid[B]): B =
        ChunkInstances.foldMap(fa.toChunk)(f)

      override def foldM[G[_], A, B](fa: NonEmptyChunk[A], z: B)(f: (B, A) => G[B])(implicit G: Monad[G]): G[B] =
        ChunkInstances.foldM(fa.toChunk, z)(f)

      override def find[A](fa: NonEmptyChunk[A])(f: A => Boolean): Option[A] =
        fa.find(f)

      override def exists[A](fa: NonEmptyChunk[A])(p: A => Boolean): Boolean =
        fa.exists(p)

      override def forall[A](fa: NonEmptyChunk[A])(p: A => Boolean): Boolean =
        fa.forall(p)

      override def toList[A](fa: NonEmptyChunk[A]): List[A] =
        fa.toList

      // UnorderedFoldable
      override def size[A](fa: NonEmptyChunk[A]): Long =
        fa.length.toLong

      // SemigroupK
      override def combineK[A](a: NonEmptyChunk[A], b: NonEmptyChunk[A]): NonEmptyChunk[A] =
        a ++ b

      // Align
      override def functor: Functor[NonEmptyChunk] =
        this

      override def align[A, B](fa: NonEmptyChunk[A], fb: NonEmptyChunk[B]): NonEmptyChunk[Ior[A, B]] =
        alignWith(fa, fb)(identity)

      override def alignWith[A, B, C](fa: NonEmptyChunk[A], fb: NonEmptyChunk[B])(f: Ior[A, B] => C): NonEmptyChunk[C] =
        NonEmptyChunk.nonEmpty(ChunkInstances.alignWith(fa.toChunk, fb.toChunk)(f))
    }
}

trait CatsNonEmptyChunkInstances1 {

  implicit def nonEmptyChunkHash[A: Hash]: Hash[NonEmptyChunk[A]] =
    Hash.by((a: NonEmptyChunk[A]) => a.toChunk)(zio.interop.catz.core.chunkHash)

  implicit def nonEmptyChunkEq[A: Eq]: Eq[NonEmptyChunk[A]] =
    Eq.by((a: NonEmptyChunk[A]) => a.toChunk)(zio.interop.catz.core.chunkEq)
}

private class NonEmptyChunkSemigroup[A] extends Semigroup[NonEmptyChunk[A]] {
  override def combine(x: NonEmptyChunk[A], y: NonEmptyChunk[A]): NonEmptyChunk[A] =
    x ++ y
}
