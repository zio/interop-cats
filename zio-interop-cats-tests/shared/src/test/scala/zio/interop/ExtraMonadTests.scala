package zio.interop

import cats.kernel.laws.discipline.catsLawsIsEqToProp
import cats.{ Eq, Monad }
import org.scalacheck.Prop
import org.typelevel.discipline.Laws

trait ExtraMonadTests[F[_]] extends Laws {
  def laws: ExtraMonadLaws[F]

  def monadExtras[A](implicit EqFInt: Eq[F[Int]]): RuleSet =
    new RuleSet {
      def name: String                  = "monadExtras"
      def bases: Seq[(String, RuleSet)] = Nil
      def parents: Seq[RuleSet]         = Nil
      def props: Seq[(String, Prop)]    =
        Seq[(String, Prop)]("tailRecM construction stack safety" -> Prop.lzy(laws.tailRecMConstructionStackSafety))
    }
}

object ExtraMonadTests {
  def apply[F[_]: Monad]: ExtraMonadTests[F] =
    new ExtraMonadTests[F] {
      def laws: ExtraMonadLaws[F] = ExtraMonadLaws[F]
    }
}
