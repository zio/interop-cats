package zio.interop

import cats._
import cats.kernel.laws.discipline._
import cats.laws.discipline.arbitrary._
import cats.laws.discipline.{ SerializableTests => _, _ }
import zio.Chunk
import zio.interop.catz.core._

class CatsChunkSpec extends CatsSpecBase {

  checkAll("Chunk[Int] with Option", TraverseTests[Chunk].traverse[Int, Int, Int, Set[Int], Option, Option])
  checkAll("Traverse[Chunk]", SerializableTests.serializable(Traverse[Chunk]))

  checkAll("Chunk[Int]", MonadTests[Chunk].monad[Int, Int, Int])
  checkAll("Monad[Chunk]", SerializableTests.serializable(Monad[Chunk]))

  checkAll("Chunk[Int]", AlternativeTests[Chunk].alternative[Int, Int, Int])
  checkAll("Alternative[Chunk]", SerializableTests.serializable(Alternative[Chunk]))

  checkAll("Chunk[Int]", CoflatMapTests[Chunk].coflatMap[Int, Int, Int])
  checkAll("Coflatmap[Chunk]", SerializableTests.serializable(CoflatMap[Chunk]))

  checkAll("Chunk[Int]", AlignTests[Chunk].align[Int, Int, Int, Int])
  checkAll("Align[Chunk]", SerializableTests.serializable(Align[Chunk]))

  checkAll("Chunk[Int]", TraverseFilterTests[Chunk].traverseFilter[Int, Int, Int])
  checkAll("TraverseFilter[Chunk]", SerializableTests.serializable(TraverseFilter[Chunk]))

  checkAll("Chunk[Int]", ShortCircuitingTests[Chunk].foldable[Int])
  checkAll("Chunk[Int]", ShortCircuitingTests[Chunk].traverse[Int])
  checkAll("Chunk[Int]", ShortCircuitingTests[Chunk].traverseFilter[Int])

  checkAll("Chunk[Int]", MonoidTests[Chunk[Int]].monoid)
  checkAll("Monoid[Chunk]", SerializableTests.serializable(Monoid[Chunk[Int]]))

  checkAll("Chunk[Int]", OrderTests[Chunk[Int]].order)
  checkAll("Order[Chunk]", SerializableTests.serializable(Order[Chunk[Int]]))

  checkAll("Chunk[Int]", PartialOrderTests[Chunk[Int]].partialOrder)
  checkAll("PartialOrder[Chunk[Int]]", SerializableTests.serializable(PartialOrder[Chunk[Int]]))

  checkAll("Chunk[Int]", HashTests[Chunk[Int]].hash)
  checkAll("Hash[Chunk[Int]]", SerializableTests.serializable(Hash[Chunk[Int]]))

  checkAll("Chunk[Int]", EqTests[Chunk[Int]].eqv)
  checkAll("Eq[Chunk[Int]]", SerializableTests.serializable(Eq[Chunk[Int]]))

  test("traverse is stack-safe") {
    val chunk  = Chunk.fromIterable(0 until 100000)
    val sumAll = Traverse[Chunk].traverse(chunk)(i => () => i).apply().sum
    assert(sumAll == chunk.sum)
  }
}
