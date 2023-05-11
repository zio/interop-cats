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

import cats.Applicative
import cats.mtl.*
import zio.{CanFail, FiberRef, ZEnvironment, ZIO}
import zio.internal.stacktracer.InteropTracer

abstract class CatsMtlPlatform extends CatsMtlInstances

abstract class CatsMtlInstances {

  implicit def zioLocal[R, E](implicit ev: Applicative[ZIO[R, E, _]]): Local[ZIO[R, E, _], ZEnvironment[R]] =
    new Local[ZIO[R, E, _], ZEnvironment[R]] {
      override def applicative: Applicative[ZIO[R, E, _]]                                          = ev
      override def ask[E2 >: ZEnvironment[R]]: ZIO[R, E, E2]                                       = ZIO.environment
      override def local[A](fa: ZIO[R, E, A])(f: ZEnvironment[R] => ZEnvironment[R]): ZIO[R, E, A] =
        fa.provideSomeEnvironment(f)(InteropTracer.newTrace(f))
    }

  implicit def zioAsk[R1, R <: R1, E](implicit ev: Applicative[ZIO[R, E, _]]): Ask[ZIO[R, E, _], ZEnvironment[R1]] =
    new Ask[ZIO[R, E, _], ZEnvironment[R1]] {
      override def applicative: Applicative[ZIO[R, E, _]]     = ev
      override def ask[R2 >: ZEnvironment[R1]]: ZIO[R, E, R2] = ZIO.environment
    }

  implicit def zioHandle[R, E](implicit ev: Applicative[ZIO[R, E, _]]): Handle[ZIO[R, E, _], E] =
    new Handle[ZIO[R, E, _], E] {
      override def applicative: Applicative[ZIO[R, E, _]]                              = ev
      override def raise[E2 <: E, A](e: E2): ZIO[R, E, A]                              = ZIO.fail(e)
      override def handleWith[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] =
        fa.catchAll(f)(implicitly[CanFail[E]], InteropTracer.newTrace(f))
    }

  implicit def fiberRefLocal[R, E](implicit fiberRef: FiberRef[R], ev: Applicative[ZIO[R, E, _]]): Local[ZIO[R, E, _], R] = new Local[ZIO[R, E, _], R] {
    override def local[A](fa: ZIO[R, E, A])(f: R => R): ZIO[R, E, A] = fiberRef.locallyWith(f)(fa)

    override def applicative: Applicative[ZIO[R, E, *]] = ev

    override def ask[E2 >: R]: ZIO[R, E, E2] = fiberRef.get
  }

  implicit def fiberRefAsk[R, E](implicit fiberRef: FiberRef[R], ev: Applicative[ZIO[R, E, _]]): Ask[ZIO[R, E, _], R] = new Ask[ZIO[R, E, _], R] {
    override def applicative: Applicative[ZIO[R, E, *]] = ev
    override def ask[E2 >: R]: ZIO[R, E, E2] = fiberRef.get
  }

}
