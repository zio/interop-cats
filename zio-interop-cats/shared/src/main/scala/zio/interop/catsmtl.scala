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

import cats.mtl._
import cats.Applicative
import zio.{ CanFail, ZIO }
import zio.internal.stacktracer.{ Tracer => CoreTracer }
import zio.internal.stacktracer.InteropTracer

abstract class CatsMtlPlatform extends CatsMtlInstances

abstract class CatsMtlInstances {

  implicit def zioApplicativeHandle[R, E](implicit ev: Applicative[ZIO[R, E, *]]): Handle[ZIO[R, E, *], E] =
    new Handle[ZIO[R, E, *], E] {
      override def applicative: Applicative[ZIO[R, E, *]] = ev
      override def raise[E2 <: E, A](e: E2): ZIO[R, E, A] = ZIO.fail(e)(CoreTracer.newTrace)
      override def handleWith[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] =
        fa.catchAll(f)(implicitly[CanFail[E]], InteropTracer.newTrace(f))
    }

}
