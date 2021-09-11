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

import java.time.{ Duration, OffsetDateTime }

import cats.effect.{ Effect, LiftIO }
import zio.{ Runtime, ZEnv, Schedule => ZSchedule }

/**
 * @see zio.ZSchedule
 */
sealed abstract class Schedule[F[+_], -In, +Out] { self =>
  import Schedule._

  type State

  protected def underlying: ZSchedule.WithState[State, ZEnv, In, Out]

  /**
   * @see zio.ZSchedule.&&
   */
  def &&[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying && that.underlying)

  /**
   * @see zio.ZSchedule.***
   */
  def ***[In2, Out2](that: Schedule[F, In2, Out2]): Schedule[F, (In, In2), (Out, Out2)] =
    Schedule(underlying *** that.underlying)

  /**
   * @see zio.ZSchedule.*>
   */
  def *>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
    Schedule(underlying *> that.underlying)

  /**
   * @see zio.ZSchedule.++
   */
  def ++[In1 <: In, Out2 >: Out](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
    Schedule(underlying ++ that.underlying)

  /**
   * @see zio.ZSchedule.+++
   */
  def +++[In2, Out2](that: Schedule[F, In2, Out2]): Schedule[F, Either[In, In2], Either[Out, Out2]] =
    Schedule(self.underlying +++ that.underlying)

  /**
   * @see zio.ZSchedule.<||>
   */
  def <||>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Either[Out, Out2]] =
    Schedule(self.underlying <||> that.underlying)

  /**
   * @see zio.ZSchedule.<*
   */
  def <*[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out] =
    Schedule(self.underlying <* that.underlying)

  /**
   * @see zio.ZSchedule.<*>
   */
  def <*>[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying <*> that.underlying)

  /**
   * @see zio.ZSchedule.<<<
   */
  def <<<[In2](that: Schedule[F, In2, In]): Schedule[F, In2, Out] =
    Schedule(self.underlying <<< that.underlying)

  /**
   * @see zio.ZSchedule.>>>
   */
  def >>>[Out2](that: Schedule[F, Out, Out2]): Schedule[F, In, Out2] =
    Schedule(self.underlying >>> that.underlying)

  /**
   * @see zio.ZSchedule.||
   */
  def ||[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying || that.underlying)

  /**
   * @see zio.ZSchedule.|||
   */
  def |||[Out1 >: Out, In2](that: Schedule[F, In2, Out1]): Schedule[F, Either[In, In2], Out1] =
    Schedule(
      (self.underlying ||| that.underlying)
        .asInstanceOf[ZSchedule.WithState[(self.State, that.State), ZEnv, Either[In, In2], Out1]]
    )

  /**
   * @see zio.ZSchedule.addDelay
   */
  def addDelay(f: Out => Duration): Schedule[F, In, Out] =
    Schedule(underlying.addDelay(f))

  /**
   * @see zio.ZSchedule.addDelayM
   */
  def addDelayM(f: Out => F[Duration])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.addDelayZIO(out => fromEffect(f(out)).orDie))

  /**
   * @see zio.ZSchedule.andThen
   */
  def andThen[In1 <: In, Out2 >: Out](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
    Schedule(self.underlying andThen that.underlying)

  /**
   * @see zio.ZSchedule.andThenEither
   */
  def andThenEither[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Either[Out, Out2]] =
    Schedule(self.underlying andThenEither that.underlying)

  /**
   * @see zio.ZSchedule.as
   */
  def as[Out2](out2: => Out2): Schedule[F, In, Out2] =
    Schedule(underlying.as(out2))

  /**
   * @see zio.ZSchedule.check
   */
  def check[In11 <: In](test: (In11, Out) => Boolean): Schedule[F, In11, Out] =
    Schedule(underlying.check(test))

  /**
   * @see zio.ZSchedule.checkM
   */
  def checkM[In1 <: In](test: (In1, Out) => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In1, Out] =
    Schedule(underlying.checkZIO((in1, out) => fromEffect(test(in1, out)).orDie))

  /**
   * @see zio.ZSchedule.collectAll
   */
  def collectAll: Schedule[F, In, List[Out]] =
    Schedule(underlying.collectAll.map((out: zio.Chunk[Out]) => out.toList))

  /**
   * @see zio.ZSchedule.compose
   */
  def compose[In2](that: Schedule[F, In2, In]): Schedule[F, In2, Out] =
    Schedule(self.underlying compose that.underlying)

  /**
   * @see zio.ZSchedule.intersectWith
   */
  def intersectWith[In1 <: In, Out2](
    that: Schedule[F, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying.intersectWith(that.underlying)(f))

  /**
   * @see zio.ZSchedule.combineWith
   */
  @deprecated("use intersectWith", "2.1.5")
  def combineWith[In1 <: In, Out2](
    that: Schedule[F, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)] =
    intersectWith(that)(f)

  /**
   * @see zio.ZSchedule.unionWith
   */
  def unionWith[In1 <: In, Out2](
    that: Schedule[F, In1, Out2]
  )(f: (Interval, Interval) => Interval): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying.unionWith(that.underlying)(f))

  /**
   * @see zio.ZSchedule.contramap
   */
  def contramap[In2](f: In2 => In): Schedule[F, In2, Out] =
    Schedule(underlying.contramap(f))

  /**
   * @see zio.ZSchedule.delayed
   */
  def delayed(f: Duration => Duration): Schedule[F, In, Out] =
    Schedule(underlying.delayed(f))

  /**
   * @see zio.ZSchedule.delayedM
   */
  def delayedM(f: Duration => F[Duration])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.delayedZIO(d => fromEffect(f(d)).orDie))

  /**
   * @see zio.ZSchedule.dimap
   */
  def dimap[In2, Out2](f: In2 => In, g: Out => Out2): Schedule[F, In2, Out2] =
    Schedule(underlying.dimap(f, g))

  /**
   * @see zio.ZSchedule.driver
   */
  def driver(implicit R: Runtime[ZEnv], F: LiftIO[F]): F[Schedule.Driver[F, State, In, Out]] =
    toEffect(underlying.driver.map(driver => new Schedule.Driver(driver)))

  /**
   * @see zio.ZSchedule.either
   */
  def either[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying either that.underlying)

  /**
   * @see zio.ZSchedule.eitherWith
   */
  def eitherWith[In1 <: In, Out2, Out3](that: Schedule[F, In1, Out2])(f: (Out, Out2) => Out3): Schedule[F, In1, Out3] =
    Schedule(underlying.eitherWith(that.underlying)(f))

  /**
   * @see zio.ZSchedule.ensuring
   */
  def ensuring(finalizer: F[Any])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.ensuring(fromEffect(finalizer).orDie))

  /**
   * @see zio.ZSchedule.first
   */
  def first[X]: Schedule[F, (In, X), (Out, X)] =
    Schedule(underlying.first)

  /**
   * @see zio.ZSchedule.fold
   */
  def fold[Z](z: Z)(f: (Z, Out) => Z): Schedule[F, In, Z] =
    Schedule(underlying.fold(z)(f))

  /**
   * @see zio.ZSchedule.foldM
   */
  def foldM[Z](z: Z)(f: (Z, Out) => F[Z])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Z] =
    Schedule(underlying.foldZIO(z)((z2, out) => fromEffect(f(z2, out)).orDie))

  /**
   * @see zio.ZSchedule.forever
   */
  def forever: Schedule[F, In, Out] =
    Schedule(underlying.forever)

  /**
   * @see zio.ZSchedule.jittered
   */
  def jittered: Schedule[F, In, Out] =
    Schedule(underlying.jittered)

  /**
   * @see zio.ZSchedule.jittered
   */
  def jittered(min: Double, max: Double): Schedule[F, In, Out] =
    Schedule(underlying.jittered(min, max))

  /**
   * @see zio.ZSchedule.left
   */
  def left[X]: Schedule[F, Either[In, X], Either[Out, X]] =
    Schedule(underlying.left)

  /**
   * @see zio.ZSchedule.map
   */
  def map[Out2](f: Out => Out2): Schedule[F, In, Out2] =
    Schedule(underlying.map(f))

  /**
   * @see zio.ZSchedule.mapM
   */
  def mapM[Out2](f: Out => F[Out2])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out2] =
    Schedule(underlying.mapZIO(out => fromEffect(f(out)).orDie))

  /**
   * @see zio.ZSchedule.modifyDelay
   */
  def modifyDelay(f: (Out, Duration) => Duration): Schedule[F, In, Out] =
    Schedule(underlying.modifyDelay((f)))

  /**
   * @see zio.ZSchedule.modifyDelayM
   */
  def modifyDelayM(f: (Out, Duration) => F[Duration])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.modifyDelayZIO((out, duration) => fromEffect(f(out, duration)).orDie))

  /**
   * @see zio.ZSchedule.repetitions
   */
  def repetitions: Schedule[F, In, Int] =
    Schedule(underlying.repetitions)

  /**
   * @see zio.ZSchedule.resetAfter
   */
  def resetAfter(duration: Duration): Schedule[F, In, Out] =
    Schedule(underlying.resetAfter(duration))

  /**
   * @see zio.ZSchedule.resetWhen
   */
  def resetWhen(f: Out => Boolean): Schedule[F, In, Out] =
    Schedule(underlying.resetWhen(f))

  /**
   * @see zio.ZSchedule.right
   */
  def right[X]: Schedule[F, Either[X, In], Either[X, Out]] =
    Schedule(underlying.right)

  /**
   * @see zio.ZSchedule.run
   */
  def run(now: OffsetDateTime, input: Iterable[In])(implicit R: Runtime[ZEnv], F: LiftIO[F]): F[List[Out]] =
    toEffect(underlying.run(now, input).map(_.toList))

  /**
   * @see zio.ZSchedule.second
   */
  def second[X]: Schedule[F, (X, In), (X, Out)] =
    Schedule(underlying.second)

  /**
   * @see zio.ZSchedule.tapInput
   */
  def tapInput[In1 <: In](f: In1 => F[Any])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In1, Out] =
    Schedule(underlying.tapInput(in => fromEffect(f(in)).orDie))

  /**
   * @see zio.ZSchedule.tapOutput
   */
  def tapOutput(f: Out => F[Any])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.tapOutput(out => fromEffect(f(out)).orDie))

  /**
   * @see zio.ZSchedule.unit
   */
  def unit: Schedule[F, In, Unit] =
    Schedule(underlying.unit)

  /**
   * @see zio.ZSchedule.untilInput
   */
  def untilInput[In1 <: In](f: In1 => Boolean): Schedule[F, In1, Out] =
    Schedule(underlying.untilInput(f))

  /**
   * @see zio.ZSchedule.untilInputM
   */
  def untilInputM[In1 <: In](f: In1 => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In1, Out] =
    Schedule(underlying.untilInputZIO(in => fromEffect(f(in)).orDie))

  /**
   * @see zio.ZSchedule.untilOutput
   */
  def untilOutput(f: Out => Boolean): Schedule[F, In, Out] =
    Schedule(underlying.untilOutput(f))

  /**
   * @see zio.ZSchedule.untilOutputM
   */
  def untilOutputM(f: Out => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.untilOutputZIO(out => fromEffect(f(out)).orDie))

  /**
   * @see zio.ZSchedule.whileInput
   */
  def whileInput[In1 <: In](f: In1 => Boolean): Schedule[F, In1, Out] =
    Schedule(underlying.whileInput(f))

  /**
   * @see zio.ZSchedule.whileInputM
   */
  def whileInputM[In1 <: In](f: In1 => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In1, Out] =
    Schedule(underlying.whileInputZIO(in => fromEffect(f(in)).orDie))

  /**
   * @see zio.ZSchedule.whileOutput
   */
  def whileOutput(f: Out => Boolean): Schedule[F, In, Out] =
    Schedule(underlying.whileOutput(f))

  /**
   * @see zio.ZSchedule.whileOutputM
   */
  def whileOutputM(f: Out => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, In, Out] =
    Schedule(underlying.whileOutputZIO(out => fromEffect(f(out)).orDie))

  /**
   * @see zio.ZSchedule.zip
   */
  def zip[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, (Out, Out2)] =
    Schedule(self.underlying zip that.underlying)

  /**
   * @see zio.ZSchedule.zipLeft
   */
  def zipLeft[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out] =
    Schedule(self.underlying zipLeft that.underlying)

  /**
   * @see zio.ZSchedule.zipRight
   */
  def zipRight[In1 <: In, Out2](that: Schedule[F, In1, Out2]): Schedule[F, In1, Out2] =
    Schedule(self.underlying zipRight that.underlying)

  /**
   * @see zio.ZSchedule.zipWith
   */
  def zipWith[In1 <: In, Out2, Out3](that: Schedule[F, In1, Out2])(f: (Out, Out2) => Out3): Schedule[F, In1, Out3] =
    Schedule(self.underlying.zipWith(that.underlying)(f))
}

object Schedule {

  /**
   * @see zio.ZSchedule.collectAll
   */
  def collectAll[F[+_], A]: Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectAll.map(_.toList))

  /**
   * @see zio.ZSchedule.collectWhile
   */
  def collectWhile[F[+_], A](f: A => Boolean): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectWhile(f).map(_.toList))

  /**
   * @see zio.ZSchedule.collectWhileM
   */
  def collectWhileM[F[+_], A](f: A => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectWhileZIO((a: A) => fromEffect(f(a)).orDie).map(_.toList))

  /**
   * @see zio.ZSchedule.collectUntil
   */
  def collectUntil[F[+_], A](f: A => Boolean): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectUntil(f).map(_.toList))

  /**
   * @see zio.ZSchedule.collectUntilM
   */
  def collectUntilM[F[+_], A](f: A => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, A, List[A]] =
    Schedule(ZSchedule.collectUntilZIO((a: A) => fromEffect(f(a)).orDie).map(_.toList))

  /**
   * @see zio.ZSchedule.delayed
   */
  def delayed[F[+_], In, Out](schedule: Schedule[F, In, Duration]): Schedule[F, In, Duration] =
    Schedule(ZSchedule.delayed(schedule.underlying))

  /**
   * @see zio.ZSchedule.recurWhile
   */
  def recurWhile[F[+_], A](f: A => Boolean): Schedule[F, A, A] =
    Schedule(ZSchedule.recurWhile(f))

  /**
   * @see zio.ZSchedule.recurWhileM
   */
  def recurWhileM[F[+_], A](f: A => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurWhileZIO(a => fromEffect(f(a)).orDie))

  /**
   * @see zio.ZSchedule.recurWhileEquals
   */
  def recurWhileEquals[F[+_], A](a: => A): Schedule[F, A, A] =
    Schedule(ZSchedule.recurWhileEquals(a))

  /**
   * @see zio.ZSchedule.recurUntil
   */
  def recurUntil[F[+_], A](f: A => Boolean): Schedule[F, A, A] =
    Schedule(ZSchedule.recurUntil(f))

  /**
   * @see zio.ZSchedule.recurUntilM
   */
  def recurUntilM[F[+_], A](f: A => F[Boolean])(implicit R: Runtime[Any], F: Effect[F]): Schedule[F, A, A] =
    Schedule(ZSchedule.recurUntilZIO(a => fromEffect(f(a)).orDie))

  /**
   * @see zio.ZSchedule.recurUntilEquals
   */
  def recurUntilEquals[F[+_], A](a: => A): Schedule[F, A, A] =
    Schedule(ZSchedule.recurUntilEquals(a))

  /**
   * @see zio.ZSchedule.recurUntil
   */
  def recurUntil[F[+_], A, B](pf: PartialFunction[A, B]): Schedule[F, A, Option[B]] =
    Schedule(ZSchedule.recurUntil(pf))

  /**
   * @see zio.ZSchedule.duration
   */
  def duration[F[+_]](duration: Duration): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.duration(duration))

  /**
   * @see zio.ZSchedule.v
   */
  def elapsed[F[+_]]: Schedule[F, Any, Duration] =
    Schedule(ZSchedule.elapsed)

  /**
   * @see zio.ZSchedule.exponential
   */
  def exponential[F[+_]](base: Duration, factor: Double = 2.0): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.exponential(base, factor))

  /**
   * @see zio.ZSchedule.fibonacci
   */
  def fibonacci[F[+_]](one: Duration): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.fibonacci(one))

  /**
   * @see zio.ZSchedule.fixed
   */
  def fixed[F[+_]](interval: Duration): Schedule[F, Any, Long] =
    Schedule(ZSchedule.fixed(interval))

  /**
   * @see zio.ZSchedule.forever
   */
  def forever[F[+_]]: Schedule[F, Any, Long] =
    Schedule(ZSchedule.forever)

  /**
   * @see zio.ZSchedule.fromDuration
   */
  def fromDuration[F[+_]](duration: Duration): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.fromDuration(duration))

  /**
   * @see zio.ZSchedule.fromDurations
   */
  def fromDurations[F[+_]](duration: Duration, durations: Duration*): Schedule[F, Any, Duration] =
    Schedule(
      ZSchedule
        .fromDurations(duration, durations: _*)
        .asInstanceOf[ZSchedule.WithState[(::[Duration], Boolean), Any, Any, Duration]]
    )

  /**
   * @see zio.ZSchedule.fromFunction
   */
  def fromFunction[F[+_], A, B](f: A => B): Schedule[F, A, B] =
    Schedule(ZSchedule.fromFunction(f))

  /**
   * @see zio.ZSchedule.count
   */
  def count[F[+_]]: Schedule[F, Any, Long] =
    Schedule(ZSchedule.count)

  /**
   * @see zio.ZSchedule.v
   */
  def identity[F[+_], A]: Schedule[F, A, A] =
    Schedule(ZSchedule.identity)

  /**
   * @see zio.ZSchedule.linear
   */
  def linear[F[+_]](base: Duration): Schedule[F, Any, Duration] =
    Schedule(ZSchedule.linear(base))

  /**
   * @see zio.ZSchedule.once
   */
  def once[F[+_]]: Schedule[F, Any, Unit] =
    Schedule(ZSchedule.once)

  /**
   * @see zio.ZSchedule.recurs
   */
  def recurs[F[+_]](n: Long): Schedule[F, Any, Long] =
    Schedule(ZSchedule.recurs(n))

  /**
   * @see zio.ZSchedule.recurs
   */
  def recurs[F[+_]](n: Int): Schedule[F, Any, Long] =
    Schedule(ZSchedule.recurs(n))

  /**
   * @see zio.ZSchedule.spaced
   */
  def spaced[F[+_]](duration: Duration): Schedule[F, Any, Long] =
    Schedule(ZSchedule.spaced(duration))

  /**
   * @see zio.ZSchedule.stop
   */
  def stop[F[+_]]: Schedule[F, Any, Unit] =
    Schedule(ZSchedule.stop)

  /**
   * @see zio.ZSchedule.succeed
   */
  def succeed[F[+_], A](a: => A): Schedule[F, Any, A] =
    Schedule(ZSchedule.succeed(a))

  /**
   * @see zio.ZSchedule.unfold
   */
  def unfold[F[+_], A](a: => A)(f: A => A): Schedule[F, Any, A] =
    Schedule(ZSchedule.unfold(a)(f))

  /**
   * @see zio.ZSchedule.windowed
   */
  def windowed[F[+_]](interval: Duration): Schedule[F, Any, Long] =
    Schedule(ZSchedule.windowed(interval))

  final class Driver[F[+_], +State, -In, +Out] private[Schedule] (
    private[Schedule] val underlying: ZSchedule.Driver[State, ZEnv, In, Out]
  ) {
    def next(in: In)(implicit R: Runtime[ZEnv], F: LiftIO[F]): F[Either[None.type, Out]] =
      toEffect(underlying.next(in).either)
    def last(implicit R: Runtime[ZEnv], F: LiftIO[F]): F[Either[NoSuchElementException, Out]] =
      toEffect(underlying.last.either)
    def reset(implicit R: Runtime[ZEnv], F: LiftIO[F]): F[Unit] =
      toEffect(underlying.reset)
  }

  type Interval = ZSchedule.Interval

  type WithState[F[+_], State0, In, Out] =
    Schedule[F, In, Out] { type State = State0 }

  private def apply[F[+_], State0, In, Out](
    underlying0: ZSchedule.WithState[State0, ZEnv, In, Out]
  ): Schedule[F, In, Out] =
    new Schedule[F, In, Out] {
      type State = State0
      val underlying: ZSchedule.WithState[State, ZEnv, In, Out] = underlying0
    }
}
