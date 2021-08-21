package zio.interop

import cats.*
import cats.kernel.laws.discipline.*
import cats.laws.discipline.*
import cats.kernel.laws.discipline.SerializableTests
import cats.laws.discipline.arbitrary.*
import org.scalacheck.{ Arbitrary, Cogen }
import zio.{ Chunk, NonEmptyChunk }
import zio.interop.catz.core.*

class CatsNonEmptyChunkSpec extends ZioSpecBase {

  implicit def arbitraryNonEmptyChunk[A](implicit A: Arbitrary[A]): Arbitrary[NonEmptyChunk[A]] =
    Arbitrary(
      implicitly[Arbitrary[Chunk[A]]].arbitrary.flatMap(fa => A.arbitrary.map(a => NonEmptyChunk.fromIterable(a, fa)))
    )

  implicit def cogenNonEmptyChunk[A](implicit A: Cogen[A]): Cogen[NonEmptyChunk[A]] =
    Cogen[Chunk[A]].contramap(_.toChunk)

  checkAll("NonEmptyChunk[Int]", SemigroupKTests[NonEmptyChunk].semigroupK[Int])
  checkAll("SemigroupK[NonEmptyChunk]", SerializableTests.serializable(SemigroupK[NonEmptyChunk]))

  checkAll("NonEmptyChunk[Int]", BimonadTests[NonEmptyChunk].bimonad[Int, Int, Int])
  checkAll("Bimonad[NonEmptyChunk]", SerializableTests.serializable(Bimonad[NonEmptyChunk]))

  checkAll(
    "NonEmptyChunk[Int] with Option",
    NonEmptyTraverseTests[NonEmptyChunk].nonEmptyTraverse[Option, Int, Int, Int, Int, Option, Option]
  )
  checkAll("NonEmptyTraverse[NonEmptyChunk]", SerializableTests.serializable(Traverse[NonEmptyChunk]))

  checkAll("NonEmptyChunk[Int]", AlignTests[NonEmptyChunk].align[Int, Int, Int, Int])
  checkAll("Align[NonEmptyChunk]", SerializableTests.serializable(Align[NonEmptyChunk]))

  checkAll("NonEmptyChunk[Int]", ShortCircuitingTests[NonEmptyChunk].foldable[Int])
  checkAll("NonEmptyChunk[Int]", ShortCircuitingTests[NonEmptyChunk].traverse[Int])
  checkAll("NonEmptyChunk[Int]", ShortCircuitingTests[NonEmptyChunk].nonEmptyTraverse[Int])

  checkAll("NonEmptyChunk[Int]", SemigroupTests[NonEmptyChunk[Int]].semigroup)
  checkAll("Semigroup[NonEmptyChunk]", SerializableTests.serializable(Semigroup[NonEmptyChunk[Int]]))

  checkAll("NonEmptyChunk[Int]", OrderTests[NonEmptyChunk[Int]].order)
  checkAll("Order[NonEmptyChunk]", SerializableTests.serializable(Order[NonEmptyChunk[Int]]))

  checkAll("NonEmptyChunk[Int]", PartialOrderTests[NonEmptyChunk[Int]].partialOrder)
  checkAll("PartialOrder[NonEmptyChunk[Int]]", SerializableTests.serializable(PartialOrder[NonEmptyChunk[Int]]))

  checkAll("NonEmptyChunk[Int]", HashTests[NonEmptyChunk[Int]].hash)
  checkAll("Hash[NonEmptyChunk[Int]]", SerializableTests.serializable(Hash[NonEmptyChunk[Int]]))

  checkAll("NonEmptyChunk[Int]", EqTests[NonEmptyChunk[Int]].eqv)
  checkAll("Eq[NonEmptyChunk[Int]]", SerializableTests.serializable(Eq[NonEmptyChunk[Int]]))

}
