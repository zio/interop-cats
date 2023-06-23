/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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
package zio.internal.stacktracer

import java.util.concurrent.{ ConcurrentHashMap, ConcurrentMap }
import scala.util.matching.Regex

object InteropTracer {
  final def newTrace(f: AnyRef): Trace = {
    val clazz       = f.getClass()
    val cachedTrace = cache.get(clazz)
    if (cachedTrace == null) {
      val computedTrace = AkkaLineNumbers(f) match {
        case AkkaLineNumbers.NoSourceInfo => Tracer.instance.empty

        case AkkaLineNumbers.UnknownSourceFormat(_) => Tracer.instance.empty

        case AkkaLineNumbers.SourceFile(filename) =>
          createTrace("<unknown>", filename.intern(), 0, 0).asInstanceOf[Trace]

        case AkkaLineNumbers.SourceFileLines(filename, from, _, _, methodAnonfun) =>
        case AkkaLineNumbers.SourceFileLines(filename, from, _, classNameSlashes, methodAnonfun) =>
          val className  = classNameSlashes.replace('/', '.')
          val methodName = lambdaNamePattern
            .findFirstMatchIn(methodAnonfun)
            .flatMap(Option apply _.group(1))
            .getOrElse(methodAnonfun)

          createTrace(methodName.intern(), filename.intern(), from, 0).asInstanceOf[Trace]
      }
      cache.put(clazz, computedTrace)
      computedTrace
    } else cachedTrace
  }

  private val cache: ConcurrentMap[Class[?], Trace] = new ConcurrentHashMap[Class[?], Trace]()

  private def createTrace(location: String, file: String, line: Int, column: Int): String =
    s"$location($file:$line:$column)".intern

  private final val lambdaNamePattern: Regex = """\$anonfun\$(.+?)\$\d""".r

  private type Trace = Tracer.instance.Type with Tracer.Traced
}
