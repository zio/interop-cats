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

package zio.interop.console

import _root_.cats.Show
import zio.{ Console, Has, ZIO, ZTraceElement }

import java.io.IOException

object cats {

  /**
   * Prints the string representation of an object to the console.
   */
  def print[A](a: A)(implicit ev: Show[A], trace: ZTraceElement): ZIO[Has[Console], IOException, Unit] =
    zio.Console.print(ev.show(a))

  /**
   * Prints the string representation of an object to the console, including a newline character.
   */
  def printLine[A](a: A)(implicit ev: Show[A], trace: ZTraceElement): ZIO[Has[Console], IOException, Unit] =
    zio.Console.printLine(ev.show(a))
}
