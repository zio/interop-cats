package zio.interop

import cats.Eq
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import zio.interop.laws.CatsTestInstances

import scala.concurrent.ExecutionContext

private[zio] trait CatsSpecBase extends AnyFunSuite with FunSuiteDiscipline with Configuration with CatsTestInstances {

  def checkAllAsync(name: String, f: Ticker => Laws#RuleSet): Unit =
    checkAll(name, f(Ticker()))

  // workaround for laws `evalOn local pure` & `executionContext commutativity`
  // (ZIO cannot implement them at all due to `.executor.asEC` losing the original executionContext)
  implicit val eqForExecutionContext: Eq[ExecutionContext] =
    Eq.allEqual
}
