package zio.interop

import cats.effect.std as ce
import cats.implicits.*
import zio.interop.catz.*
import zio.interop.catz.implicits.*
import zio.{ Scope, ZIO }
import zio.test.*

object SecureRandomSpec extends ZIOSpecDefault {
  private def SR[F[_]: ce.SecureRandom]         = ce.SecureRandom[F]
  private def SRTask: ce.SecureRandom[zio.Task] = SR[zio.Task]

  def spec: Spec[TestEnvironment & Scope, Throwable] = suite("SecureRandomSpec")(
    test("betweenDouble") {
      for { values <- ZIO.collectAll(List.fill(100)(SRTask.betweenDouble(10.0, 20.0))) } yield assertTrue(
        values.forall(d => d >= 10.0 && d < 20.0)
      )
    },
    test("betweenFloat") {
      for { values <- ZIO.collectAll(List.fill(100)(SRTask.betweenFloat(10.0f, 20.0f))) } yield assertTrue(
        values.forall(f => f >= 10.0f && f < 20.0f)
      )
    },
    test("betweenInt") {
      for { values <- ZIO.collectAll(List.fill(100)(SRTask.betweenInt(10, 20))) } yield assertTrue(
        values.forall(i => i >= 10 && i < 20)
      )
    },
    test("betweenLong") {
      for { values <- ZIO.collectAll(List.fill(100)(SRTask.betweenLong(10L, 20L))) } yield assertTrue(
        values.forall(l => l >= 10L && l < 20L)
      )
    },
    test("nextAlphaNumeric is printable char") {
      for {
        values <- ZIO.collectAll(List.fill(100)(SRTask.nextAlphaNumeric))
      } yield assertTrue(values.forall(!_.isControl))
    },
    test("nextBoolean yields both values eventually") {
      for {
        values <- ZIO.collectAll(List.fill(500)(SRTask.nextBoolean))
      } yield assertTrue(values.contains(true) && values.contains(false))
    },
    test("nextBytes length matches request") {
      for {
        arr <- SRTask.nextBytes(128)
      } yield assertTrue(arr.length == 128)
    },
    test("nextDouble in [0,1)") {
      for {
        values <- ZIO.collectAll(List.fill(200)(SRTask.nextDouble))
      } yield assertTrue(values.forall(d => d >= 0.0 && d < 1.0))
    },
    test("nextFloat in [0,1)") {
      for {
        values <- ZIO.collectAll(List.fill(200)(SRTask.nextFloat))
      } yield assertTrue(values.forall(f => f >= 0.0f && f < 1.0f))
    },
    test("nextGaussian has reasonable mean and variance") {
      for {
        values  <- ZIO.collectAll(List.fill(2000)(SRTask.nextGaussian))
        mean     = values.sum / values.size
        variance = {
          val m = mean
          values.map(x => (x - m) * (x - m)).sum / values.size
        }
      } yield assertTrue(math.abs(mean) < 0.2 && math.abs(variance - 1.0) < 0.3)
    },
    test("nextInt produces diverse values") {
      for {
        values <- ZIO.collectAll(List.fill(300)(SRTask.nextInt))
      } yield assertTrue(values.distinct.size >= 200)
    },
    test("nextIntBounded stays within 0 until n (exclusive)") {
      for {
        values <- ZIO.collectAll(List.fill(200)(SRTask.nextIntBounded(10)))
      } yield assertTrue(values.forall(i => i >= 0 && i < 10))
    },
    test("nextLong produces diverse values") {
      for { values <- ZIO.collectAll(List.fill(300)(SRTask.nextLong)) } yield assertTrue(values.distinct.size >= 200)
    },
    test("nextLongBounded stays within 0 until n (exclusive)") {
      for {
        values <- ZIO.collectAll(List.fill(200)(SRTask.nextLongBounded(1000L)))
      } yield assertTrue(values.forall(l => l >= 0L && l < 1000L))
    },
    test("nextPrintableChar is printable") {
      for {
        values <- ZIO.collectAll(List.fill(100)(SRTask.nextPrintableChar))
      } yield assertTrue(values.forall(!_.isControl))
    },
    test("nextString length matches") {
      for { s <- SRTask.nextString(64) } yield assertTrue(s.length == 64)
    },
    test("shuffleList preserves elements") {
      val original = List.range(1, 21)
      for { shuffled <- SRTask.shuffleList(original) } yield assertTrue(shuffled.sorted == original.sorted)
    },
    test("shuffleVector preserves elements") {
      val original = Vector.range(1, 21)
      for { shuffled <- SRTask.shuffleVector(original) } yield assertTrue(shuffled.sorted == original.sorted)
    }
  )
}
