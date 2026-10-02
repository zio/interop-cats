package zio.interop

import cats.Eq
import cats.effect.laws.*
import zio.{ durationInt as _, * }
import zio.interop.catz.autocatch.*

import scala.concurrent.duration.*

/**
 * Runs the cats-effect laws against the `catz.autocatch` instances with defects
 * (`ZIO.die`) included in the generated effects. Under these instances a defect
 * is an ordinary error, so effects are compared by their `attempt`, which makes
 * `Cause.Die(e)` and `Cause.Fail(e)` indistinguishable.
 */
class CatsAutoCatchSpec extends ZioSpecBase {

  override def defectGenerator: Boolean = true

  override implicit def eqForTask[A: Eq](implicit ticker: Ticker): Eq[Task[A]] =
    Eq.by(asyncInstanceAutoCatch[Any].attempt(_).orDie)

  checkAllAsync(
    "Async[Task] (autocatch)",
    implicit tc => AsyncTests[Task].async[Int, Int, Int](100.millis)
  )
  checkAllAsync(
    "Temporal[Task] (autocatch)",
    implicit tc => GenTemporalTests[Task, Throwable].temporal[Int, Int, Int](100.millis)
  )
}
