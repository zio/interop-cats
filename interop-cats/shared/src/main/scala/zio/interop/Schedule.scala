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

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import zio.{ Runtime, ZEnv, Schedule as ZSchedule }

import java.time.{ Duration, OffsetDateTime }

/**
 * @see zio.ZSchedule
 */
sealed abstract class Schedule[F[+_], -In, +Out](private val underlying: ZSchedule[ZEnv, In, Out]) {
  import Schedule.*

  /**
   * @see zio.ZSchedule.&&
   */
  def &&[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.***
   */
  def ***[In2, Out2](that: Schedule[F, In2, Out2]): Schedule[F, (In, In2), (Out, Out2)]

  /**
   * @see zio.ZSchedule.*>
   */
  def *>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2]

  /**
   * @see zio.ZSchedule.++
   */
  def ++[In1 <: In, Out2 >: Out](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2]

  /**
   * @see zio.ZSchedule.+++
   */
  def +++[In2, Out2](that: Schedule[F, In2, Out2]): Schedule[F, Either[In, In2], Either[Out, Out2]]

  /**
   * @see zio.ZSchedule.<||>
   */
  def <||>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Either[Out, Out2]]

  /**
   * @see zio.ZSchedule.<*
   */
  def <*[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.<*>
   */
  def <*>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.<<<
   */
  def <<<[In2](that: Schedule[F, In2, In]): Schedule[F, In2, Out]

  /**
   * @see zio.ZSchedule.>>>
   */
  def >>>[Out2](that: Schedule[F, Out, Out2]): Schedule[F, In, Out2]

  /**
   * @see zio.ZSchedule.||
   */
  def ||[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.|||
   */
  def |||[Out1 >: Out, In2](that: Schedule[F, In2, Out1]): Schedule[F, Either[In, In2], Out1]

  /**
   * @see zio.ZSchedule.addDelay
   */
  def addDelay(f: Out => Duration): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.addDelayM
   */
  def addDelayM(f: Out => F[Duration]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.andThen
   */
  def andThen[In1 <: In, Out2 >: Out](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2]

  /**
   * @see zio.ZSchedule.andThenEither
   */
  def andThenEither[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Either[Out, Out2]]

  /**
   * @see zio.ZSchedule.as
   */
  def as[Out2](out2: => Out2): Schedule[F, In, Out2]

  /**
   * @see zio.ZSchedule.check
   */
  def check[In11 <: In](test: (In11, Out) => Boolean): Schedule[F, In11, Out]

  /**
   * @see zio.ZSchedule.checkM
   */
  def checkM[In1 <: In](test: (In1, Out) => F[Boolean]): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.collectAll
   */
  def collectAll: Schedule[F, In, List[Out]]

  /**
   * @see zio.ZSchedule.compose
   */
  def compose[In2](that: Schedule[F, In2, In]): Schedule[F, In2, Out]

  /**
   * @see zio.ZSchedule.intersectWith
   */
  def intersectWith[In1 <: In, Out2](
    that: Schedule[F, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.combineWith
   */
  @deprecated("use intersectWith", "2.1.5")
  def combineWith[In1 <: In, Out2](
    that: Schedule[F, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.unionWith
   */
  def unionWith[In1 <: In, Out2](
    that: Schedule[F, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.contramap
   */
  def contramap[In2](f: In2 => In): Schedule[F, In2, Out]

  /**
   * @see zio.ZSchedule.delayed
   */
  def delayed(f: Duration => Duration): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.delayedM
   */
  def delayedM(f: Duration => F[Duration]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.dimap
   */
  def dimap[In2, Out2](f: In2 => In, g: Out => Out2): Schedule[F, In2, Out2]

  /**
   * @see zio.ZSchedule.driver
   */
  def driver: F[Schedule.Driver[F, In, Out]]

  /**
   * @see zio.ZSchedule.either
   */
  def either[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.eitherWith
   */
  def eitherWith[In1 <: In, Out2, Out3](that: Schedule[F, In1, Out2])(f: (Out, Out2) => Out3): Schedule[F, In1, Out3]

  /**
   * @see zio.ZSchedule.ensuring
   */
  def ensuring(finalizer: F[Any]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.first
   */
  def first[X]: Schedule[F, (In, X), (Out, X)]

  /**
   * @see zio.ZSchedule.fold
   */
  def fold[Z](z: Z)(f: (Z, Out) => Z): Schedule[F, In, Z]

  /**
   * @see zio.ZSchedule.foldM
   */
  def foldM[Z](z: Z)(f: (Z, Out) => F[Z]): Schedule[F, In, Z]

  /**
   * @see zio.ZSchedule.forever
   */
  def forever: Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.jittered
   */
  def jittered: Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.jittered
   */
  def jittered(min: Double, max: Double): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.left
   */
  def left[X]: Schedule[F, Either[In, X], Either[Out, X]]

  /**
   * @see zio.ZSchedule.map
   */
  def map[Out2](f: Out => Out2): Schedule[F, In, Out2]

  /**
   * @see zio.ZSchedule.mapM
   */
  def mapM[Out2](f: Out => F[Out2]): Schedule[F, In, Out2]

  /**
   * @see zio.ZSchedule.modifyDelay
   */
  def modifyDelay(f: (Out, Duration) => Duration): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.modifyDelayM
   */
  def modifyDelayM(f: (Out, Duration) => F[Duration]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.repetitions
   */
  def repetitions: Schedule[F, In, Int]

  /**
   * @see zio.ZSchedule.resetAfter
   */
  def resetAfter(duration: Duration): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.resetWhen
   */
  def resetWhen(f: Out => Boolean): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.right
   */
  def right[X]: Schedule[F, Either[X, In], Either[X, Out]]

  /**
   * @see zio.ZSchedule.run
   */
  def run(now: OffsetDateTime, input: Iterable[In]): F[List[Out]]

  /**
   * @see zio.ZSchedule.second
   */
  def second[X]: Schedule[F, (X, In), (X, Out)]

  /**
   * @see zio.ZSchedule.tapInput
   */
  def tapInput[In1 <: In](f: In1 => F[Any]): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.tapOutput
   */
  def tapOutput(f: Out => F[Any]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.unit
   */
  def unit: Schedule[F, In, Unit]

  /**
   * @see zio.ZSchedule.untilInput
   */
  def untilInput[In1 <: In](f: In1 => Boolean): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.untilInputM
   */
  def untilInputM[In1 <: In](f: In1 => F[Boolean]): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.untilOutput
   */
  def untilOutput(f: Out => Boolean): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.untilOutputM
   */
  def untilOutputM(f: Out => F[Boolean]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.whileInput
   */
  def whileInput[In1 <: In](f: In1 => Boolean): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.whileInputM
   */
  def whileInputM[In1 <: In](f: In1 => F[Boolean]): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.whileOutput
   */
  def whileOutput(f: Out => Boolean): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.whileOutputM
   */
  def whileOutputM(f: Out => F[Boolean]): Schedule[F, In, Out]

  /**
   * @see zio.ZSchedule.zip
   */
  def zip[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)]

  /**
   * @see zio.ZSchedule.zipLeft
   */
  def zipLeft[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out]

  /**
   * @see zio.ZSchedule.zipRight
   */
  def zipRight[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2]

  /**
   * @see zio.ZSchedule.zipWith
   */
  def zipWith[In1 <: In, Out2, Out3](that: Schedule[F, In1, Out2])(f: (Out, Out2) => Out3): Schedule[F, In1, Out3]
}

object Schedule {

  /**
   * @see zio.ZSchedule.collectAll
   */
  def collectAll[F[+_]: Async: Dispatcher, A](implicit runtime: Runtime[ZEnv]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectAll.map(_.toList))

  /**
   * @see zio.ZSchedule.collectWhile
   */
  def collectWhile[F[+_]: Async: Dispatcher, A](
    f: A => Boolean
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectWhile(f).map(_.toList))

  /**
   * @see zio.ZSchedule.collectWhileM
   */
  def collectWhileM[F[+_]: Async: Dispatcher, A](
    f: A => F[Boolean]
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectWhileM((a: A) => fromEffect(f(a)).orDie).map(_.toList))

  /**
   * @see zio.ZSchedule.collectUntil
   */
  def collectUntil[F[+_]: Async: Dispatcher, A](
    f: A => Boolean
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectUntil(f).map(_.toList))

  /**
   * @see zio.ZSchedule.collectUntilM
   */
  def collectUntilM[F[+_]: Async: Dispatcher, A](
    f: A => F[Boolean]
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectUntilM((a: A) => fromEffect(f(a)).orDie).map(_.toList))

  /**
   * @see zio.ZSchedule.delayed
   */
  def delayed[F[+_]: Async: Dispatcher, In, Out](
    schedule: Schedule[F, In, Duration]
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, In, Duration] =
    Schedule(ZSchedule.delayed(schedule.underlying))

  /**
   * @see zio.ZSchedule.recurWhile
   */
  def recurWhile[F[+_]: Async: Dispatcher, A](f: A => Boolean)(implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurWhile(f))

  /**
   * @see zio.ZSchedule.recurWhileM
   */
  def recurWhileM[F[+_]: Async: Dispatcher, A](f: A => F[Boolean])(implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurWhileM(a => fromEffect(f(a)).orDie))

  /**
   * @see zio.ZSchedule.recurWhileEquals
   */
  def recurWhileEquals[F[+_]: Async: Dispatcher, A](a: => A)(implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurWhileEquals(a))

  /**
   * @see zio.ZSchedule.recurUntil
   */
  def recurUntil[F[+_]: Async: Dispatcher, A](f: A => Boolean)(implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurUntil(f))

  /**
   * @see zio.ZSchedule.recurUntilM
   */
  def recurUntilM[F[+_]: Async: Dispatcher, A](f: A => F[Boolean])(implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurUntilM(a => fromEffect(f(a)).orDie))

  /**
   * @see zio.ZSchedule.recurUntilEquals
   */
  def recurUntilEquals[F[+_]: Async: Dispatcher, A](a: => A)(implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurUntilEquals(a))

  /**
   * @see zio.ZSchedule.recurUntil
   */
  def recurUntil[F[+_]: Async: Dispatcher, A, B](
    pf: PartialFunction[A, B]
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, A, Option[B]] =
    Schedule(ZSchedule.recurUntil(pf))

  /**
   * @see zio.ZSchedule.duration
   */
  def duration[F[+_]: Async: Dispatcher](
    duration: Duration
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.duration(duration))

  /**
   * @see zio.ZSchedule.v
   */
  def elapsed[F[+_]: Async: Dispatcher](implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.elapsed)

  /**
   * @see zio.ZSchedule.exponential
   */
  def exponential[F[+_]: Async: Dispatcher](base: Duration, factor: Double = 2.0)(
    implicit runtime: Runtime[ZEnv]
  ): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.exponential(base, factor))

  /**
   * @see zio.ZSchedule.fibonacci
   */
  def fibonacci[F[+_]: Async: Dispatcher](one: Duration)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.fibonacci(one))

  /**
   * @see zio.ZSchedule.fixed
   */
  def fixed[F[+_]: Async: Dispatcher](interval: Duration)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.fixed(interval))

  /**
   * @see zio.ZSchedule.forever
   */
  def forever[F[+_]: Async: Dispatcher](implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.forever)

  /**
   * @see zio.ZSchedule.fromDuration
   */
  def fromDuration[F[+_]: Async: Dispatcher](
    duration: Duration
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.fromDuration(duration))

  /**
   * @see zio.ZSchedule.fromDurations
   */
  def fromDurations[F[+_]: Async: Dispatcher](duration: Duration, durations: Duration*)(
    implicit runtime: Runtime[ZEnv]
  ): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.fromDurations(duration, durations*))

  /**
   * @see zio.ZSchedule.fromFunction
   */
  def fromFunction[F[+_]: Async: Dispatcher, A, B](f: A => B)(implicit runtime: Runtime[ZEnv]): Schedule[F, A, B] =
    Schedule(ZSchedule.fromFunction(f))

  /**
   * @see zio.ZSchedule.count
   */
  def count[F[+_]: Async: Dispatcher](implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.count)

  /**
   * @see zio.ZSchedule.v
   */
  def identity[F[+_]: Async: Dispatcher, A](implicit runtime: Runtime[ZEnv]): Schedule[F, A, A] =
    Schedule(ZSchedule.identity)

  /**
   * @see zio.ZSchedule.linear
   */
  def linear[F[+_]: Async: Dispatcher](base: Duration)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.linear(base))

  /**
   * @see zio.ZSchedule.once
   */
  def once[F[+_]: Async: Dispatcher](implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Unit] =
    Schedule(ZSchedule.once)

  /**
   * @see zio.ZSchedule.recurs
   */
  def recurs[F[+_]: Async: Dispatcher](n: Long)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.recurs(n))

  /**
   * @see zio.ZSchedule.recurs
   */
  def recurs[F[+_]: Async: Dispatcher](n: Int)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.recurs(n))

  /**
   * @see zio.ZSchedule.spaced
   */
  def spaced[F[+_]: Async: Dispatcher](duration: Duration)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.spaced(duration))

  /**
   * @see zio.ZSchedule.stop
   */
  def stop[F[+_]: Async: Dispatcher](implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Unit] =
    Schedule(ZSchedule.stop)

  /**
   * @see zio.ZSchedule.succeed
   */
  def succeed[F[+_]: Async: Dispatcher, A](a: => A)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, A] =
    Schedule(ZSchedule.succeed(a))

  /**
   * @see zio.ZSchedule.unfold
   */
  def unfold[F[+_]: Async: Dispatcher, A](a: => A)(f: A => A)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, A] =
    Schedule(ZSchedule.unfold(a)(f))

  /**
   * @see zio.ZSchedule.windowed
   */
  def windowed[F[+_]: Async: Dispatcher](interval: Duration)(implicit runtime: Runtime[ZEnv]): Schedule[F, Any, Long] =
    Schedule(ZSchedule.windowed(interval))

  final class Driver[F[+_]: Async, -In, +Out] private[Schedule] (
    underlying: ZSchedule.Driver[ZEnv, In, Out]
  )(implicit runtime: Runtime[ZEnv]) {
    def next(in: In): F[Either[None.type, Out]] =
      underlying.next(in).either.toEffect[F]
    val last: F[Either[NoSuchElementException, Out]] =
      underlying.last.either.toEffect[F]
    val reset: F[Unit] =
      underlying.reset.toEffect[F]
  }

  type Interval = ZSchedule.Interval

  private def apply[F[+_]: Async: Dispatcher, In, Out](
    underlying: ZSchedule[ZEnv, In, Out]
  )(implicit runtime: Runtime[ZEnv]): Schedule[F, In, Out] =
    new Schedule[F, In, Out](underlying) {
      def &&[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying && that.underlying)
      def ***[In2, Out2](that: Schedule[F, In2, Out2]): Schedule[F, (In, In2), (Out, Out2)] =
        Schedule(underlying *** that.underlying)
      def *>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
        Schedule(underlying *> that.underlying)
      def ++[In1 <: In, Out2 >: Out](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
        Schedule(underlying ++ that.underlying)
      def +++[In2, Out2](that: Schedule[F, In2, Out2]): Schedule[F, Either[In, In2], Either[Out, Out2]] =
        Schedule(underlying +++ that.underlying)
      def <||>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Either[Out, Out2]] =
        Schedule(underlying <||> that.underlying)
      def <*[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out] =
        Schedule(underlying <* that.underlying)
      def <*>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying <*> that.underlying)
      def <<<[In2](that: Schedule[F, In2, In]): Schedule[F, In2, Out] =
        Schedule(underlying <<< that.underlying)
      def >>>[Out2](that: Schedule[F, Out, Out2]): Schedule[F, In, Out2] =
        Schedule(underlying >>> that.underlying)
      def ||[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying || that.underlying)
      def |||[Out1 >: Out, In2](that: Schedule[F, In2, Out1]): Schedule[F, Either[In, In2], Out1] =
        Schedule(underlying ||| that.underlying)
      def addDelay(f: Out => Duration): Schedule[F, In, Out] =
        Schedule(underlying.addDelay(f))
      def addDelayM(f: Out => F[Duration]): Schedule[F, In, Out] =
        Schedule(underlying.addDelayM(out => fromEffect(f(out)).orDie))
      def andThen[In1 <: In, Out2 >: Out](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
        Schedule(underlying andThen that.underlying)
      def andThenEither[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Either[Out, Out2]] =
        Schedule(underlying andThenEither that.underlying)
      def as[Out2](out2: => Out2): Schedule[F, In, Out2] =
        Schedule(underlying.as(out2))
      def check[In11 <: In](test: (In11, Out) => Boolean): Schedule[F, In11, Out] =
        Schedule(underlying.check(test))
      def checkM[In1 <: In](test: (In1, Out) => F[Boolean]): Schedule[F, In1, Out] =
        Schedule(underlying.checkM((in1, out) => fromEffect(test(in1, out)).orDie))
      def collectAll: Schedule[F, In, List[Out]] =
        Schedule(underlying.collectAll.map(_.toList))
      def compose[In2](that: Schedule[F, In2, In]): Schedule[F, In2, Out] =
        Schedule(underlying compose that.underlying)
      def intersectWith[In1 <: In, Out2](
        that: Schedule[F, In1, Out2]
      )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying.intersectWith(that.underlying)(f))
      @deprecated("use intersectWith", "2.1.5")
      def combineWith[In1 <: In, Out2](
        that: Schedule[F, In1, Out2]
      )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)] =
        intersectWith(that)(f)
      def unionWith[In1 <: In, Out2](
        that: Schedule[F, In1, Out2]
      )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying.unionWith(that.underlying)(f))
      def contramap[In2](f: In2 => In): Schedule[F, In2, Out] =
        Schedule(underlying.contramap(f))
      def delayed(f: Duration => Duration): Schedule[F, In, Out] =
        Schedule(underlying.delayed(f))
      def delayedM(f: Duration => F[Duration]): Schedule[F, In, Out] =
        Schedule(underlying.delayedM(d => fromEffect(f(d)).orDie))
      def dimap[In2, Out2](f: In2 => In, g: Out => Out2): Schedule[F, In2, Out2] =
        Schedule(underlying.dimap(f, g))
      val driver: F[Schedule.Driver[F, In, Out]] =
        underlying.driver.map(driver => new Schedule.Driver(driver)).toEffect[F]
      def either[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying either that.underlying)
      def eitherWith[In1 <: In, Out2, Out3](
        that: Schedule[F, In1, Out2]
      )(f: (Out, Out2) => Out3): Schedule[F, In1, Out3] =
        Schedule(underlying.eitherWith(that.underlying)(f))
      def ensuring(finalizer: F[Any]): Schedule[F, In, Out] =
        Schedule(underlying.ensuring(fromEffect(finalizer).orDie))
      def first[X]: Schedule[F, (In, X), (Out, X)] =
        Schedule(underlying.first[X])
      def fold[Z](z: Z)(f: (Z, Out) => Z): Schedule[F, In, Z] =
        Schedule(underlying.fold(z)(f))
      def foldM[Z](z: Z)(f: (Z, Out) => F[Z]): Schedule[F, In, Z] =
        Schedule(underlying.foldM(z)((z2, out) => fromEffect(f(z2, out)).orDie))
      def forever: Schedule[F, In, Out] =
        Schedule(underlying.forever)
      def jittered: Schedule[F, In, Out] =
        Schedule(underlying.jittered)
      def jittered(min: Double, max: Double): Schedule[F, In, Out] =
        Schedule(underlying.jittered(min, max))
      def left[X]: Schedule[F, Either[In, X], Either[Out, X]] =
        Schedule(underlying.left[X])
      def map[Out2](f: Out => Out2): Schedule[F, In, Out2] =
        Schedule(underlying.map(f))
      def mapM[Out2](f: Out => F[Out2]): Schedule[F, In, Out2] =
        Schedule(underlying.mapM(out => fromEffect(f(out)).orDie))
      def modifyDelay(f: (Out, Duration) => Duration): Schedule[F, In, Out] =
        Schedule(underlying.modifyDelay(f))
      def modifyDelayM(f: (Out, Duration) => F[Duration]): Schedule[F, In, Out] =
        Schedule(underlying.modifyDelayM((out, duration) => fromEffect(f(out, duration)).orDie))
      def repetitions: Schedule[F, In, Int] =
        Schedule(underlying.repetitions)
      def resetAfter(duration: Duration): Schedule[F, In, Out] =
        Schedule(underlying.resetAfter(duration))
      def resetWhen(f: Out => Boolean): Schedule[F, In, Out] =
        Schedule(underlying.resetWhen(f))
      def right[X]: Schedule[F, Either[X, In], Either[X, Out]] =
        Schedule(underlying.right[X])
      def run(now: OffsetDateTime, input: Iterable[In]): F[List[Out]] =
        underlying.run(now, input).map(_.toList).toEffect[F]
      def second[X]: Schedule[F, (X, In), (X, Out)] =
        Schedule(underlying.second[X])
      def tapInput[In1 <: In](f: In1 => F[Any]): Schedule[F, In1, Out] =
        Schedule(underlying.tapInput(in => fromEffect(f(in)).orDie))
      def tapOutput(f: Out => F[Any]): Schedule[F, In, Out] =
        Schedule(underlying.tapOutput(out => fromEffect(f(out)).orDie))
      def unit: Schedule[F, In, Unit] =
        Schedule(underlying.unit)
      def untilInput[In1 <: In](f: In1 => Boolean): Schedule[F, In1, Out] =
        Schedule(underlying.untilInput(f))
      def untilInputM[In1 <: In](f: In1 => F[Boolean]): Schedule[F, In1, Out] =
        Schedule(underlying.untilInputM(in => fromEffect(f(in)).orDie))
      def untilOutput(f: Out => Boolean): Schedule[F, In, Out] =
        Schedule(underlying.untilOutput(f))
      def untilOutputM(f: Out => F[Boolean]): Schedule[F, In, Out] =
        Schedule(underlying.untilOutputM(out => fromEffect(f(out)).orDie))
      def whileInput[In1 <: In](f: In1 => Boolean): Schedule[F, In1, Out] =
        Schedule(underlying.whileInput(f))
      def whileInputM[In1 <: In](f: In1 => F[Boolean]): Schedule[F, In1, Out] =
        Schedule(underlying.whileInputM(in => fromEffect(f(in)).orDie))
      def whileOutput(f: Out => Boolean): Schedule[F, In, Out] =
        Schedule(underlying.whileOutput(f))
      def whileOutputM(f: Out => F[Boolean]): Schedule[F, In, Out] =
        Schedule(underlying.whileOutputM(out => fromEffect(f(out)).orDie))
      def zip[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
        Schedule(underlying zip that.underlying)
      def zipLeft[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out] =
        Schedule(underlying zipLeft that.underlying)
      def zipRight[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
        Schedule(underlying zipRight that.underlying)
      def zipWith[In1 <: In, Out2, Out3](that: Schedule[F, In1, Out2])(f: (Out, Out2) => Out3): Schedule[F, In1, Out3] =
        Schedule(underlying.zipWith(that.underlying)(f))
    }
}
