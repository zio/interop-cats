package zio.interop

import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.prop.Configuration
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

trait CustomFunSuiteDiscipline extends FunSuiteDiscipline { self: AnyFunSuiteLike & Configuration =>
  final def checkAll_(name: String, ruleSet: Laws#RuleSet)(implicit
    config: PropertyCheckConfiguration,
    prettifier: Prettifier,
    pos: Position
  ): Unit =
    // todo
    for ((id, prop) <- ruleSet.all.properties if !id.contains("onCancel associates over uncancelable boundary"))
      test(s"$name.$id") {
        Checkers.check(prop)(convertConfiguration(config), prettifier, pos)
      }
}
