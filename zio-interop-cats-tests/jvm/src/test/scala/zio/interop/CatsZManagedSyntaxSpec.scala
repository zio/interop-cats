package zio.interop

import java.util.concurrent.TimeUnit

import cats.effect.{ ContextShift, ExitCase, Resource, IO => CIO }
import zio.interop.catz._
import zio.test.Assertion._
import zio.test._
import zio.{ blocking, Exit, Promise, Ref, Reservation, Runtime, Task, ZEnv, ZIO, ZManaged }

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global

object CatsZManagedSyntaxSpec extends DefaultRunnableSpec {

  val runtime                                = Runtime.default
  def unsafeRun[R, E, A](p: ZIO[ZEnv, E, A]) = runtime.unsafeRun(p)

  def spec =
    suite("CatsZManagedSyntaxSpec")(
      suite("toManaged")(
        test("calls finalizers correctly when use is interrupted") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay { effects += x }.void) {
              case (_, ExitCase.Canceled) =>
                CIO.delay { effects += x + 1 }.void
              case _ => CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use(_ => ZIO.interrupt.unit)
          }

          unsafeRun(testCase.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has failed") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay { effects += x }.void) {
              case (_, ExitCase.Error(_)) =>
                CIO.delay { effects += x + 1 }.void
              case _ =>
                CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use(_ => ZIO.fail(new RuntimeException()).unit)
          }

          unsafeRun(testCase.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has died") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay { effects += x }.void) {
              case (_, ExitCase.Error(_)) =>
                CIO.delay { effects += x + 1 }.void
              case _ =>
                CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use(_ => ZIO.die(new RuntimeException()).unit)
          }

          unsafeRun(testCase.sandbox.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers should not run if exception is thrown in acquisition") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay(effects += x) *> CIO.delay(throw new RuntimeException()).void)(
              _ => CIO.delay { effects += x + 1 }.void
            )

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use_(ZIO.unit)
          }

          unsafeRun(testCase.sandbox.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1)))
        },
        test("calls finalizers correctly when using the resource") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay { effects += x }.void)(_ => CIO.delay { effects += x }.void)

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use_(ZIO.unit)
          }

          unsafeRun(testCase)
          assert(effects.toList)(equalTo(List(1, 1)))
        },
        test("composing with other managed should calls finalizers in correct order") {

          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay { effects += x }.void)(_ => CIO.delay { effects += x }.void)

          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

          val testCase = {
            val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            val managed2: ZManaged[Any, Throwable, Unit] = man(2)
            (managed1 *> managed2).use_(ZIO.unit)
          }

          unsafeRun(testCase)
          assert(effects.toList)(equalTo(List(1, 2, 2, 1)))
        }
      ),
      suite("toManagedZIO")(
        test("calls finalizers correctly when use is interrupted") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(Task { effects += x }.unit) {
              case (_, ExitCase.Canceled) =>
                Task { effects += x + 1 }.unit
              case _ => Task.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use(_ => ZIO.interrupt.unit)
          }

          unsafeRun(testCase.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has failed") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(Task { effects += x }.unit) {
              case (_, ExitCase.Error(_)) =>
                Task { effects += x + 1 }.unit
              case _ =>
                Task.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use(_ => ZIO.fail(new RuntimeException()).unit)
          }

          unsafeRun(testCase.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has died") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(Task { effects += x }.unit) {
              case (_, ExitCase.Error(_)) =>
                Task { effects += x + 1 }.unit
              case _ =>
                Task.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use(_ => ZIO.die(new RuntimeException()).unit)
          }

          unsafeRun(testCase.sandbox.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers should not run if exception is thrown in acquisition") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(Task(effects += x) *> Task(throw new RuntimeException()).unit)(
              _ => Task { effects += x + 1 }.unit
            )

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use_(ZIO.unit)
          }

          unsafeRun(testCase.sandbox.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1)))
        },
        test("calls finalizers correctly when using the resource") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(Task { effects += x }.unit)(_ => Task { effects += x }.unit)

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use_(ZIO.unit)
          }

          unsafeRun(testCase)
          assert(effects.toList)(equalTo(List(1, 1)))
        },
        test("composing with other managed should calls finalizers in correct order") {

          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(Task { effects += x }.unit)(_ => Task { effects += x }.unit)

          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x))

          val testCase = {
            val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            val managed2: ZManaged[Any, Throwable, Unit] = man(2)
            (managed1 *> managed2).use_(ZIO.unit)
          }

          unsafeRun(testCase)
          assert(effects.toList)(equalTo(List(1, 2, 2, 1)))
        }
      ),
      suite("toResource")(
        test("calls finalizers when using resource") {
          val effects = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.make(ZIO.effectTotal(effects += x).unit)(_ => ZIO.effectTotal(effects += x + 1))

          val testCase = ZIO.runtime[Any].flatMap { implicit r =>
            man(1).toResource.use(_ => ZIO.unit)
          }
          unsafeRun(testCase)
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource fails") {
          val effects = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.makeExit(ZIO.effectTotal(effects += x).unit) {
              case (_, Exit.Failure(_)) =>
                ZIO.effectTotal(effects += x + 1)
              case _ =>
                ZIO.unit
            }

          val testCase = ZIO.runtime[Any].flatMap { implicit r =>
            man(1).toResource.use(_ => ZIO.fail(new RuntimeException()).unit)
          }
          unsafeRun(testCase.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource is canceled") {
          val effects = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.makeExit(ZIO.effectTotal(effects += x).unit) {
              case (_, e) if e.interrupted =>
                ZIO.effectTotal(effects += x + 1)
              case _ =>
                ZIO.unit
            }

          val testCase = ZIO.runtime[Any].flatMap { implicit r =>
            man(1).toResource.use(_ => ZIO.interrupt)
          }
          unsafeRun(testCase.orElse(ZIO.unit))
          assert(effects.toList)(equalTo(List(1, 2)))
        },
        test("acquisition of Reservation preserves cancellability in new F") {
          unsafeRun {
            ZIO.runtime[Any] >>= {
              implicit runtime =>
                implicit val ctx: ContextShift[CIO] = CIO.contextShift(global)

                (for {
                  startLatch <- Promise.make[Nothing, Unit]
                  endLatch   <- Promise.make[Nothing, Unit]
                  release    <- Ref.make(false)
                  managed = ZManaged.reserve(
                    Reservation(
                      acquire = startLatch.succeed(()) *> ZIO.never,
                      release = _ => release.set(true) *> endLatch.succeed(())
                    )
                  )
                  resource = managed.toResource[CIO]

                  _ <- blocking.effectBlockingInterrupt {
                        resource
                          .use(_ => CIO.unit)
                          .start
                          .flatMap(
                            f =>
                              CIO(runtime.unsafeRun(startLatch.await))
                                .flatMap(_ => f.cancel)
                          )
                          .unsafeRunSync()
                      }.timeoutFail("startLatch timed out")(zio.duration.Duration(10, TimeUnit.SECONDS))
                  _   <- endLatch.await.timeoutFail("endLatch timed out")(zio.duration.Duration(10, TimeUnit.SECONDS))
                  res <- release.get
                } yield assert(res)(equalTo(true))).provideLayer(ZEnv.live)
            }
          }
        }
      )
    )
}
