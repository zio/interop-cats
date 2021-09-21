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
import zio.ZIO

abstract class CatsMtlPlatform extends CatsMtlInstances

abstract class CatsMtlInstances {

  implicit def zioLocal[R, E](implicit ev: Applicative[ZIO[R, E, *]]): Local[ZIO[R, E, *], R] =
    new Local[ZIO[R, E, *], R] {
      override def applicative: Applicative[ZIO[R, E, *]]              = ev
      override def ask[E2 >: R]: ZIO[R, E, E2]                         = ZIO.environment
      override def local[A](fa: ZIO[R, E, A])(f: R => R): ZIO[R, E, A] = ZIO.accessM(fa provide f(_))
    }

  implicit def zioApplicativeAsk[R1, R <: R1, E](
    implicit ev: Applicative[ZIO[R, E, *]]
  ): Ask[ZIO[R, E, *], R1] =
    new Ask[ZIO[R, E, *], R1] {
      override def applicative: Applicative[ZIO[R, E, *]] = ev
      override def ask[R2 >: R1]: ZIO[R, E, R2]           = ZIO.environment
    }

  implicit def zioApplicativeHandle[R, E](implicit ev: Applicative[ZIO[R, E, *]]): Handle[ZIO[R, E, *], E] =
    new Handle[ZIO[R, E, *], E] {
      override def applicative: Applicative[ZIO[R, E, *]]                              = ev
      override def raise[E2 <: E, A](e: E2): ZIO[R, E, A]                              = ZIO.fail(e)
      override def handleWith[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] = fa.catchAll(f)
    }

}
