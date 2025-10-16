package zio.interop
import cats.effect.kernel.Async
import cats.effect.std as ce
import zio.{ Random as ZRandom, Runtime }

object SecureRandom {
  final def apply[F[_]: Async](implicit runtime: Runtime[Any]): ce.SecureRandom[F] = new ce.SecureRandom[F] {
    def betweenDouble(minInclusive: Double, maxExclusive: Double): F[Double] =
      ZRandom.nextDoubleBetween(minInclusive, maxExclusive).toEffect[F]
    def betweenFloat(minInclusive: Float, maxExclusive: Float): F[Float]     =
      ZRandom.nextFloatBetween(minInclusive, maxExclusive).toEffect[F]
    def betweenInt(minInclusive: Int, maxExclusive: Int): F[Int]             =
      ZRandom.nextIntBetween(minInclusive, maxExclusive).toEffect[F]
    def betweenLong(minInclusive: Long, maxExclusive: Long): F[Long]         =
      ZRandom.nextLongBetween(minInclusive, maxExclusive).toEffect[F]

    def nextAlphaNumeric: F[Char]                    = ZRandom.nextPrintableChar.toEffect[F]
    def nextBoolean: F[Boolean]                      = ZRandom.nextBoolean.toEffect[F]
    def nextBytes(n: Int): F[Array[Byte]]            = ZRandom.nextBytes(n).map(_.toArray).toEffect[F]
    def nextDouble: F[Double]                        = ZRandom.nextDouble.toEffect[F]
    def nextFloat: F[Float]                          = ZRandom.nextFloat.toEffect[F]
    def nextGaussian: F[Double]                      = ZRandom.nextGaussian.toEffect[F]
    def nextInt: F[Int]                              = ZRandom.nextInt.toEffect[F]
    def nextIntBounded(n: Int): F[Int]               = ZRandom.nextIntBounded(n).toEffect[F]
    def nextLong: F[Long]                            = ZRandom.nextLong.toEffect[F]
    def nextLongBounded(n: Long): F[Long]            = ZRandom.nextLongBounded(n).toEffect[F]
    def nextPrintableChar: F[Char]                   = ZRandom.nextPrintableChar.toEffect[F]
    def nextString(length: Int): F[String]           = ZRandom.nextString(length).toEffect[F]
    def shuffleList[A](l: List[A]): F[List[A]]       = ZRandom.shuffle(l).toEffect[F]
    def shuffleVector[A](v: Vector[A]): F[Vector[A]] = ZRandom.shuffle(v.toList).map(_.toVector).toEffect[F]
  }
}
