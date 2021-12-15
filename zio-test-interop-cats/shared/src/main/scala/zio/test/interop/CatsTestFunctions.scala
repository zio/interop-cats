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

package zio.test.interop

import cats.effect.Effect
import zio.interop.catz.taskEffectInstance
import zio.test._
import zio.{ RIO, Task, ZTraceElement }

trait CatsTestFunctions {

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  final def assertF[F[_], R, A](
    value: F[A],
    assertion: Assertion[A]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    assertM(fromEffect(value))(assertion)

  /**
   * Checks the effectual test passes for "sufficient" numbers of samples from
   * the given random variable.
   */
  final def checkF[F[_], R <: TestConfig, A](
    rv: Gen[R, A]
  )(test: A => F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    check(rv)(a => fromEffect(test(a)))

  /**
   * A version of `checkM` that accepts two random variables.
   */
  final def checkF[F[_], R <: TestConfig, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => F[TestResult]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    check(rv1, rv2)((a, b) => fromEffect(test(a, b)))

  /**
   * A version of `checkM` that accepts three random variables.
   */
  final def checkF[F[_], R <: TestConfig, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => F[TestResult]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    check(rv1, rv2, rv3)((a, b, c) => fromEffect(test(a, b, c)))

  /**
   * A version of `checkM` that accepts four random variables.
   */
  final def checkF[F[_], R <: TestConfig, A, B, C, D](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D]
  )(
    test: (A, B, C, D) => F[TestResult]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    check(rv1, rv2, rv3, rv4)((a, b, c, d) => fromEffect(test(a, b, c, d)))

  /**
   * Checks the effectual test passes for all values from the given random
   * variable. This is useful for deterministic `Gen` that comprehensively
   * explore all possibilities in a given domain.
   */
  final def checkAllF[F[_], R <: TestConfig, A](
    rv: Gen[R, A]
  )(test: A => F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkAll(rv)(a => fromEffect(test(a)))

  /**
   * A version of `checkAllM` that accepts two random variables.
   */
  final def checkAllF[F[_], R <: TestConfig, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => F[TestResult]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkAll(rv1, rv2)((a, b) => fromEffect(test(a, b)))

  /**
   * A version of `checkAllM` that accepts three random variables.
   */
  final def checkAllF[F[_], R <: TestConfig, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => F[TestResult]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkAll(rv1, rv2, rv3)((a, b, c) => fromEffect(test(a, b, c)))

  /**
   * A version of `checkAllM` that accepts four random variables.
   */
  final def checkAllF[F[_], R <: TestConfig, A, B, C, D](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D]
  )(
    test: (A, B, C, D) => F[TestResult]
  )(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkAll(rv1, rv2, rv3, rv4)((a, b, c, d) => fromEffect(test(a, b, c, d)))

  /**
   * Checks the effectual test passes for the specified number of samples from
   * the given random variable.
   */
  final def checkSomeF[F[_], R <: TestConfig, A](
    rv: Gen[R, A]
  )(n: Int)(test: A => F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkN(n)(rv)(a => fromEffect(test(a)))

  /**
   * A version of `checkSomeM` that accepts two random variables.
   */
  final def checkSomeF[F[_], R <: TestConfig, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    n: Int
  )(test: (A, B) => F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkN(n)(rv1, rv2)((a, b) => fromEffect(test(a, b)))

  /**
   * A version of `checkSomeM` that accepts three random variables.
   */
  final def checkSomeF[F[_], R <: TestConfig, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    n: Int
  )(test: (A, B, C) => F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkN(n)(rv1, rv2, rv3)((a, b, c) => fromEffect(test(a, b, c)))

  /**
   * A version of `checkSomeM` that accepts four random variables.
   */
  final def checkSomeF[F[_], R <: TestConfig, A, B, C, D](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D]
  )(
    n: Int
  )(test: (A, B, C, D) => F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): RIO[R, TestResult] =
    checkN(n)(rv1, rv2, rv3, rv4)((a, b, c, d) => fromEffect(test(a, b, c, d)))

  /**
   * Builds a spec with a single effectful test.
   */
  final def testF[F[_]](
    label: String
  )(assertion: F[TestResult])(implicit F: Effect[F], trace: ZTraceElement): ZSpec[Any, Throwable] =
    test(label)(fromEffect(assertion))

  private def fromEffect[F[_], A](eff: F[A])(implicit F: Effect[F], trace: ZTraceElement): Task[A] =
    Task.runtime.flatMap(taskEffectInstance(_).liftIO(F.toIO(eff)))
}
