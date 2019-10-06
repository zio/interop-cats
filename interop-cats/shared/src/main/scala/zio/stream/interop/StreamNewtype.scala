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

package zio.stream.interop
import zio.stream.ZStream

private[interop] trait StreamNewtype {
  type Base
  trait Tag extends Any

  type T[-R, +E, +A] <: Base with Tag

  def apply[R, E, A](zstream: ZStream[R, E, A]): T[R, E, A] =
    zstream.asInstanceOf[T[R, E, A]]

  def unwrap[R, E, A](t: T[R, E, A]): ZStream[R, E, A] =
    t.asInstanceOf[ZStream[R, E, A]]
}

private[interop] object Par extends StreamNewtype
