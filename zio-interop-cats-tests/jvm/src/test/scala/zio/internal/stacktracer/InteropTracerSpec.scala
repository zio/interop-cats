package zio.internal.stacktracer

import zio.test._
import zio.Trace

object InteropTracerSpec extends ZIOSpecDefault {

  private val myLambda: () => Any = () => ()
  override def spec               =
    suite("InteropTracerSpec")(
      test("lambda tracing") {

        val result = InteropTracer.newTrace(myLambda)

        assertTrue(result == "myLambda(InteropTracerSpec.scala:8:0)".asInstanceOf[Trace])
      },
      test("tracing 'by name' parameter") {

        def check[A](f: => A): Trace = {
          val byName: () => A = () => f
          InteropTracer.newTrace(byName)
        }

        val result = check(42)

        assertTrue(result == "spec(InteropTracerSpec.scala:24:0)".asInstanceOf[Trace])
      }
    ).@@(TestAspect.exceptScala3)
}
