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

import cats._
import cats.data.{ Chain, Ior }
import cats.kernel.instances.StaticMethods
import cats.syntax.functor._
import zio.{ Chunk, ChunkBuilder }

import scala.annotation.tailrec

/**
 * The same instances for [[Chunk]] that Cats defines for [[Vector]].
 */
trait CatsChunkInstances extends CatsKernelChunkInstances {

  /** @see [[cats.instances.VectorInstances.catsStdInstancesForVector]] */
  implicit val chunkStdInstances
    : Traverse[Chunk] with Monad[Chunk] with Alternative[Chunk] with CoflatMap[Chunk] with Align[Chunk] =
    new Traverse[Chunk] with Monad[Chunk] with Alternative[Chunk] with CoflatMap[Chunk] with Align[Chunk] {

      // Functor
      override def map[A, B](fa: Chunk[A])(f: A => B): Chunk[B] =
        fa.map(f)

      // Applicative
      override def pure[A](x: A): Chunk[A] = Chunk.single(x)

      override def map2[A, B, Z](fa: Chunk[A], fb: Chunk[B])(f: (A, B) => Z): Chunk[Z] =
        if (fb.isEmpty) Chunk.empty                // do O(1) work if either is empty
        else fa.flatMap(a => fb.map(b => f(a, b))) // already O(1) if fa is empty

      private[this] val evalEmpty: Eval[Chunk[Nothing]] = Eval.now(Chunk.empty)

      override def map2Eval[A, B, Z](fa: Chunk[A], fb: Eval[Chunk[B]])(f: (A, B) => Z): Eval[Chunk[Z]] =
        if (fa.isEmpty) evalEmpty // no need to evaluate fb
        else fb.map(fb => map2(fa, fb)(f))

      // FlatMap
      override def flatMap[A, B](fa: Chunk[A])(f: A => Chunk[B]): Chunk[B] =
        fa.flatMap(f)

      override def tailRecM[A, B](a: A)(fn: A => Chunk[Either[A, B]]): Chunk[B] =
        Chunk.fromIterable(FlatMap[Vector].tailRecM(a)(a => fn(a).toVector))

      // CoflatMap
      override def coflatMap[A, B](fa: Chunk[A])(f: Chunk[A] => B): Chunk[B] = {
        @tailrec def loop(chunk: Chunk[A], b: ChunkBuilder[B]): Chunk[B] = chunk match {
          case _ +: as => loop(as, b += f(chunk))
          case _       => b.result()
        }
        loop(fa, ChunkBuilder.make[B](fa.size))
      }

      // Traverse
      override def traverse[G[_], A, B](fa: Chunk[A])(f: A => G[B])(implicit G: Applicative[G]): G[Chunk[B]] =
        Chain.traverseViaChain(fa)(f).map(c => Chunk.fromIterable(c.toVector))

      override def mapWithIndex[A, B](fa: Chunk[A])(f: (A, Int) => B): Chunk[B] =
        fa.zipWithIndex.map(ai => f(ai._1, ai._2))

      override def zipWithIndex[A](fa: Chunk[A]): Chunk[(A, Int)] =
        fa.zipWithIndex

      // Foldable
      override def foldLeft[A, B](fa: Chunk[A], b: B)(f: (B, A) => B): B =
        fa.foldLeft(b)(f)

      override def foldRight[A, B](fa: Chunk[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = {
        def loop(i: Int): Eval[B] =
          if (i < fa.length) {
            f(fa(i), Eval.defer(loop(i + 1)))
          } else lb
        Eval.defer(loop(0))
      }

      override def reduceLeftOption[A](fa: Chunk[A])(f: (A, A) => A): Option[A] =
        fa.reduceLeftOption(f)

      override def get[A](fa: Chunk[A])(idx: Long): Option[A] =
        if (idx < Int.MaxValue && fa.size > idx && idx >= 0) Some(fa(idx.toInt)) else None

      override def collectFirst[A, B](fa: Chunk[A])(pf: PartialFunction[A, B]): Option[B] =
        fa.collectFirst(pf)

      override def collectFirstSome[A, B](fa: Chunk[A])(f: A => Option[B]): Option[B] =
        fa.collectFirst(Function.unlift(f))

      override def fold[A](fa: Chunk[A])(implicit A: Monoid[A]): A = A.combineAll(fa)

      override def foldMap[A, B](fa: Chunk[A])(f: A => B)(implicit B: Monoid[B]): B =
        B.combineAll(fa.iterator.map(f))

      override def foldM[G[_], A, B](fa: Chunk[A], z: B)(f: (B, A) => G[B])(implicit G: Monad[G]): G[B] = {
        val length = fa.length
        G.tailRecM((z, 0)) {
          case (b, i) =>
            if (i < length) f(b, fa(i)).map(b => Left((b, i + 1)))
            else G.pure(Right(b))
        }
      }

      override def find[A](fa: Chunk[A])(f: A => Boolean): Option[A] = fa.find(f)

      override def exists[A](fa: Chunk[A])(p: A => Boolean): Boolean = fa.exists(p)

      override def forall[A](fa: Chunk[A])(p: A => Boolean): Boolean = fa.forall(p)

      override def toList[A](fa: Chunk[A]): List[A] = fa.toList

      override def isEmpty[A](fa: Chunk[A]): Boolean = fa.isEmpty

      // UnorderedFoldable
      override def size[A](fa: Chunk[A]): Long = fa.size.toLong

      // MonoidK
      override def algebra[A]: Monoid[Chunk[A]] = new ChunkMonoid[A]

      override def empty[A]: Chunk[A] = Chunk.empty

      // SemigroupK
      override def combineK[A](x: Chunk[A], y: Chunk[A]): Chunk[A] = x ++ y

      // Align
      override def functor: Functor[Chunk] = this

      def align[A, B](fa: Chunk[A], fb: Chunk[B]): Chunk[A Ior B] =
        alignWith(fa, fb)(identity)

      override def alignWith[A, B, C](fa: Chunk[A], fb: Chunk[B])(f: Ior[A, B] => C): Chunk[C] = {
        val initial = fa.zipWith(fb)((a, b) => f(Ior.both(a, b)))
        initial ++ (if (fa.size >= fb.size) {
                      fa.drop(fb.size).map(a => f(Ior.left(a)))
                    } else {
                      fb.drop(fa.size).map(b => f(Ior.right(b)))
                    })
      }
    }

  /** @see [[cats.instances.VectorInstancesBinCompat0.catsStdTraverseFilterForVector]] */
  implicit val chunkTraverseFilter: TraverseFilter[Chunk] = new TraverseFilter[Chunk] {

    // TraverseFilter
    override val traverse: Traverse[Chunk] = chunkStdInstances

    override def traverseFilter[G[_], A, B](
      fa: Chunk[A]
    )(f: A => G[Option[B]])(implicit G: Applicative[G]): G[Chunk[B]] =
      Chain.traverseFilterViaChain(fa)(f).map(c => Chunk.fromIterable(c.toVector))

    // FunctorFilter
    override def mapFilter[A, B](fa: Chunk[A])(f: A => Option[B]): Chunk[B] =
      fa.collect(Function.unlift(f))

    override def collect[A, B](fa: Chunk[A])(f: PartialFunction[A, B]): Chunk[B] = fa.collect(f)

    override def filter[A](fa: Chunk[A])(f: A => Boolean): Chunk[A] = fa.filter(f)

    override def filterNot[A](fa: Chunk[A])(f: A => Boolean): Chunk[A] = fa.filterNot(f)
  }
}

trait CatsKernelChunkInstances extends CatsKernelChunkInstances1 {

  implicit def chunkOrder[A: Order]: Order[Chunk[A]] = new ChunkOrder[A]

  implicit def chunkMonoid[A]: Monoid[Chunk[A]] = new ChunkMonoid[A]
}

/** @see [[cats.kernel.instances.VectorOrder]] */
private class ChunkOrder[A](implicit ev: Order[A]) extends Order[Chunk[A]] {
  override def compare(xs: Chunk[A], ys: Chunk[A]): Int =
    if (xs eq ys) 0
    else StaticMethods.iteratorCompare(xs.iterator, ys.iterator)
}

/** @see [[cats.kernel.instances.VectorMonoid]] */
private class ChunkMonoid[A] extends Monoid[Chunk[A]] {
  override def empty: Chunk[A] = Chunk.empty

  override def combine(x: Chunk[A], y: Chunk[A]): Chunk[A] = x ++ y

  override def combineN(x: Chunk[A], n: Int): Chunk[A] =
    StaticMethods.combineNIterable(ChunkBuilder.make[A](), x, n)
}

private[interop] trait CatsKernelChunkInstances1 extends CatsKernelChunkInstances2 {

  implicit def chunkPartialOrder[A: PartialOrder]: PartialOrder[Chunk[A]] = new ChunkPartialOrder[A]

  implicit def chunkHash[A: Hash]: Hash[Chunk[A]] = new ChunkHash[A]
}

/** @see [[cats.kernel.instances.VectorPartialOrder]] */
private class ChunkPartialOrder[A](implicit ev: PartialOrder[A]) extends PartialOrder[Chunk[A]] {
  override def partialCompare(xs: Chunk[A], ys: Chunk[A]): Double =
    if (xs eq ys) 0.0
    else StaticMethods.iteratorPartialCompare(xs.iterator, ys.iterator)
}

/** @see [[cats.kernel.instances.VectorHash]] */
private class ChunkHash[A](implicit ev: Hash[A]) extends ChunkEq[A] with Hash[Chunk[A]] {
  override def hash(xs: Chunk[A]): Int = StaticMethods.orderedHash(xs)
}

private[interop] trait CatsKernelChunkInstances2 {

  implicit def chunkEq[A: Eq]: Eq[Chunk[A]] = new ChunkEq[A]
}

/** @see [[cats.kernel.instances.VectorEq]] */
private class ChunkEq[A](implicit ev: Eq[A]) extends Eq[Chunk[A]] {
  override def eqv(xs: Chunk[A], ys: Chunk[A]): Boolean =
    if (xs eq ys) true
    else StaticMethods.iteratorEq(xs.iterator, ys.iterator)
}
