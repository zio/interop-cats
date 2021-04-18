//package zio.test.interop
//
//import cats.effect.IO
//import cats.effect.std.Dispatcher
//import cats.effect.unsafe.{ IORuntime, IORuntimeConfig, Scheduler }
//import zio.blocking.Blocking
//import zio.duration._
//import zio.test.Assertion._
//import zio.test.TestAspect._
//import zio.test._
//import zio.test.environment.TestEnvironment
//import zio.test.interop.catz.test._
//import zio.{ Runtime, ZEnv }
//
//object TestSpec extends DefaultRunnableSpec {
//  implicit val zioRuntime: Runtime[ZEnv] = Runtime.default
//  val (scheduler, shutdown)              = Scheduler.createDefaultScheduler()
//  implicit val ioRuntime: IORuntime = IORuntime(
//    zioRuntime.platform.executor.asEC,
//    zioRuntime.environment.get[Blocking.Service].blockingExecutor.asEC,
//    scheduler,
//    shutdown,
//    IORuntimeConfig()
//  )
//
//  implicit val dispatcher: Dispatcher[IO] =
//    Dispatcher[IO].allocated.unsafeRunSync()._1
//
//  override def spec: ZSpec[TestEnvironment, Any] =
//    suite("TestSpec")(
//      testF("arbitrary effects can be tested") {
//        for {
//          result <- IO("Hello from Cats!")
//        } yield assert(result)(equalTo("Hello from Cats!"))
//      },
//      timeout(0.milliseconds) {
//        testF("ZIO interruption is tied to F interruption") {
//          for {
//            _ <- IO.never[Nothing]
//          } yield assertCompletes
//        }
//      } @@ failing
//    )
//}
