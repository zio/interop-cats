//package zio.interop
//
//import cats.effect.{ Concurrent, Resource }
//import zio.{ Promise, Runtime, Task }
//import zio.interop.catz._
//
//class CatsInteropSpec extends catzSpecZIOBase {
//
//  test("cats fiber wrapped in Resource can be canceled") {
//    val io = for {
//      p        <- Promise.make[Nothing, Int]
//      resource = Resource.make(Concurrent[Task].start(p.succeed(1) *> Task.never))(_.cancel)
//      _        <- resource.use(_ => p.await)
//    } yield 0
//
//    val result = Runtime.default.unsafeRun(io)
//    assert(result === 0)
//  }
//}
