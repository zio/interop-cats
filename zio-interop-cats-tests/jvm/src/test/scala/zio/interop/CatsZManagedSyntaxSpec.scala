package zio.interop

import cats.effect.kernel.{ Concurrent, Resource }
import cats.effect.IO as CIO
import zio.*
import zio.interop.catz.*
import zio.managed.*
import zio.test.Assertion.*
import zio.test.*

import scala.collection.mutable

object CatsZManagedSyntaxSpec extends CatsRunnableSpec {

  def spec =
    suite("CatsZManagedSyntaxSpec")(
      suite("toManaged")(
        test("calls finalizers correctly when use is externally interrupted") {
          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay(effects += x).void) {
              case (_, Resource.ExitCase.Canceled) =>
                CIO.delay(effects += x + 1).void
              case (_, _)                          =>
                CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            Promise.make[Nothing, Unit].flatMap { latch =>
              managed
                .use(_ => latch.succeed(()) *> ZIO.never)
                .forkDaemon
                .flatMap(latch.await *> _.interrupt)
            }
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use is internally interrupted") {
          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay(effects += x).void) {
              case (_, Resource.ExitCase.Errored(_)) =>
                CIO.delay(effects += x + 1).void
              case (_, _)                            =>
                CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use(_ => ZIO.interrupt.unit)
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has failed") {
          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay(effects += x).void) {
              case (_, Resource.ExitCase.Errored(_)) =>
                CIO.delay(effects += x + 1).void
              case _                                 =>
                CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use(_ => ZIO.fail(new RuntimeException()).unit)
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has died") {
          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay(effects += x).void) {
              case (_, Resource.ExitCase.Errored(_)) =>
                CIO.delay(effects += x + 1).void
              case _                                 =>
                CIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.use(_ => ZIO.die(new RuntimeException()).unit)
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers should not run if exception is thrown in acquisition") {
          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay(effects += x) *> CIO.delay(throw new RuntimeException()).void)(_ =>
              CIO.delay(effects += x + 1).void
            )

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.useDiscard(ZIO.unit)
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1)))
        },
        test("calls finalizers correctly when using the resource") {
          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay(effects += x).void)(_ => CIO.delay(effects += x).void)

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            managed.useDiscard(ZIO.unit)
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 1)))
        },
        test("composing with other managed should calls finalizers in correct order") {

          val effects                          = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay(effects += x).void)(_ => CIO.delay(effects += x).void)

          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.acquireReleaseWith(ZIO.succeed(effects += x).unit)(_ => ZIO.succeed(effects += x))

          val testCase = {
            val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManaged
            val managed2: ZManaged[Any, Throwable, Unit] = man(2)
            (managed1 *> managed2).useDiscard(ZIO.unit)
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2, 2, 1)))
        }
      ),
      suite("toManagedZIO")(
        test("calls finalizers correctly when use is externally interrupted") {
          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(ZIO.attempt(effects += x).unit) {
              case (_, Resource.ExitCase.Canceled) =>
                ZIO.attempt(effects += x + 1).unit
              case _                               => ZIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            Promise.make[Nothing, Unit].flatMap { latch =>
              managed
                .use(_ => latch.succeed(()) *> ZIO.never)
                .forkDaemon
                .flatMap(latch.await *> _.interrupt)
            }
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use is internally interrupted") {
          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(ZIO.attempt(effects += x).unit) {
              case (_, Resource.ExitCase.Errored(_)) =>
                ZIO.attempt(effects += x + 1).unit
              case _                                 => ZIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use(_ => ZIO.interrupt.unit)
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has failed") {
          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(ZIO.attempt(effects += x).unit) {
              case (_, Resource.ExitCase.Errored(_)) =>
                ZIO.attempt(effects += x + 1).unit
              case _                                 =>
                ZIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use(_ => ZIO.fail(new RuntimeException()).unit)
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has died") {
          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(ZIO.attempt(effects += x).unit) {
              case (_, Resource.ExitCase.Errored(_)) =>
                ZIO.attempt(effects += x + 1).unit
              case _                                 =>
                ZIO.unit
            }

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.use(_ => ZIO.die(new RuntimeException()).unit)
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers should not run if exception is thrown in acquisition") {
          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(ZIO.attempt(effects += x) *> ZIO.attempt(throw new RuntimeException()).unit)(_ =>
              ZIO.attempt(effects += x + 1).unit
            )

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.useDiscard(ZIO.unit)
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1)))
        },
        test("calls finalizers correctly when using the resource") {
          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(ZIO.attempt(effects += x).unit)(_ => ZIO.attempt(effects += x).unit)

          val testCase = {
            val managed: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            managed.useDiscard(ZIO.unit)
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 1)))
        },
        test("composing with other managed should calls finalizers in correct order") {

          val effects                           = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(ZIO.attempt(effects += x).unit)(_ => ZIO.attempt(effects += x).unit)

          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.acquireReleaseWith(ZIO.succeed(effects += x).unit)(_ => ZIO.succeed(effects += x))

          val testCase = {
            val managed1: ZManaged[Any, Throwable, Unit] = res(1).toManagedZIO
            val managed2: ZManaged[Any, Throwable, Unit] = man(2)
            (managed1 *> managed2).useDiscard(ZIO.unit)
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2, 2, 1)))
        }
      ),
      suite("toResource")(
        test("calls finalizers when using resource") {
          val effects                                     = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.acquireReleaseWith(ZIO.succeed(effects += x).unit)(_ => ZIO.succeed(effects += x + 1))

          val testCase = man(1).toResource[Task].use(_ => ZIO.unit)
          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource fails") {
          val effects                                     = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.acquireReleaseExitWith(ZIO.succeed(effects += x).unit) {
              case (_, Exit.Failure(_)) =>
                ZIO.succeed(effects += x + 1)
              case _                    =>
                ZIO.unit
            }

          val testCase = man(1).toResource[Task].use(_ => ZIO.fail(new RuntimeException()).unit)
          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource is internally interrupted") {
          val effects                                     = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.acquireReleaseExitWith(ZIO.succeed(effects += x).unit) {
              case (_, Exit.Failure(c)) if !c.isInterrupted && c.failureOption.nonEmpty =>
                ZIO.succeed(effects += x + 1)
              case _                                                                    =>
                ZIO.unit
            }

          val testCase = man(1).toResource[Task].use(_ => ZIO.interrupt)
          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource is externally interrupted") { // todo
          val effects                                     = new mutable.ListBuffer[Int]
          def man(x: Int): ZManaged[Any, Throwable, Unit] =
            ZManaged.acquireReleaseExitWith(ZIO.succeed(effects += x).unit) {
              case (_, e) if e.isInterrupted =>
                ZIO.succeed(effects += x + 1)
              case _                         =>
                ZIO.unit
            }

          val exception: Option[Throwable] =
            try {
              man(1).toResource[Task].use(_ => Concurrent[Task].canceled).toEffect[cats.effect.IO].unsafeRunSync()
              None
            } catch {
              case t: Throwable => Some(t)
            }

          assert(effects.toList)(equalTo(List(1, 2))) && assertTrue(
            exception.get.getMessage.contains("The fiber was canceled")
          )
        },
        test("acquisition of Reservation preserves cancellability in new F") {
          for {
            startLatch <- Promise.make[Nothing, Unit]
            endLatch   <- Promise.make[Nothing, Unit]
            release    <- Ref.make(false)
            managed     = ZManaged.fromReservation(
                            Reservation(
                              acquire = startLatch.succeed(()) *> ZIO.never,
                              release = _ => release.set(true) *> endLatch.succeed(())
                            )
                          )
            resource    = managed.toResource[CIO]
            _          <- ZIO.attemptBlockingInterrupt {
                            resource
                              .use(_ => CIO.unit)
                              .start
                              .flatMap(fiber => startLatch.await.toEffect[CIO] *> fiber.cancel)
                              .unsafeRunSync()
                          }
            _          <- endLatch.await
            res        <- release.get
          } yield assert(res)(equalTo(true))
        }
      )
    )
}
