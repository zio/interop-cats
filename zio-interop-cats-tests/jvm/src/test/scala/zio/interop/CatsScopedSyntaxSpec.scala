package zio.interop

import cats.effect.{ ExitCase, Resource, IO => CIO }
import zio.interop.catz._
import zio.test.Assertion._
import zio.test._
import zio.{ Exit, Runtime, Scope, Task, ZIO }

import scala.collection.mutable

object CatsScopedSyntaxSpec extends ZIOSpecDefault {

  val runtime                               = Runtime.default
  def unsafeRun[R, E, A](p: ZIO[Any, E, A]) = runtime.unsafeRun(p)

  def spec =
    suite("CatsScopedSyntaxSpec")(
      suite("toScoped")(
        test("calls finalizers correctly when use is interrupted") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.makeCase(CIO.delay { effects += x }.void) {
              case (_, ExitCase.Canceled) =>
                CIO.delay { effects += x + 1 }.void
              case _ => CIO.unit
            }

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScoped
            Scope.make.flatMap(_.use[Any](scoped *> ZIO.interrupt.unit))
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
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
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScoped
            Scope.make.flatMap(_.use[Any](scoped *> ZIO.fail(new RuntimeException()).unit))
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
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
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScoped
            Scope.make.flatMap(_.use[Any](scoped *> ZIO.die(new RuntimeException()).unit))
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers should not run if exception is thrown in acquisition") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay(effects += x) *> CIO.delay(throw new RuntimeException()).void)(
              _ => CIO.delay { effects += x + 1 }.void
            )

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScoped
            Scope.make.flatMap(_.use[Any](scoped))
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1)))
        },
        test("calls finalizers correctly when using the resource") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay { effects += x }.void)(_ => CIO.delay { effects += x }.void)

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScoped
            Scope.make.flatMap(_.use[Any](scoped))
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 1)))
        },
        test("composing with other scoped should calls finalizers in correct order") {

          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[CIO, Unit] =
            Resource.make(CIO.delay { effects += x }.void)(_ => CIO.delay { effects += x }.void)

          def scope(x: Int): ZIO[Scope, Throwable, Unit] =
            ZIO.succeed(effects += x).acquireRelease(ZIO.succeed(effects += x))(ZIO.unit)

          val testCase = {
            val scoped1: ZIO[Scope, Throwable, Unit] = res(1).toScoped
            val scoped2: ZIO[Scope, Throwable, Unit] = scope(2)
            Scope.make.flatMap(_.use[Any](scoped1 *> scoped2))
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2, 2, 1)))
        }
      ),
      suite("toScopedZIO")(
        test("calls finalizers correctly when use is interrupted") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(Task.succeed { effects += x }.unit) {
              case (_, ExitCase.Canceled) =>
                Task.succeed { effects += x + 1 }.unit
              case _ => Task.unit
            }

          val testCase = {
            val scope: ZIO[Scope, Throwable, Unit] = res(1).toScopedZIO
            Scope.make.flatMap(_.use[Any](scope *> ZIO.interrupt.unit))
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has failed") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(Task.succeed { effects += x }.unit) {
              case (_, ExitCase.Error(_)) =>
                Task.succeed { effects += x + 1 }.unit
              case _ =>
                Task.unit
            }

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScopedZIO
            Scope.make.flatMap(_.use[Any](scoped *> ZIO.fail(new RuntimeException()).unit))
          }

          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers correctly when use has died") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.makeCase(Task.succeed { effects += x }.unit) {
              case (_, ExitCase.Error(_)) =>
                Task.succeed { effects += x + 1 }.unit
              case _ =>
                Task.unit
            }

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScopedZIO
            Scope.make.flatMap(_.use[Any](scoped *> ZIO.die(new RuntimeException()).unit))
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers should not run if exception is thrown in acquisition") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(Task.succeed(effects += x) *> Task.succeed(throw new RuntimeException()).unit)(
              _ => Task.succeed { effects += x + 1 }.unit
            )

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScopedZIO
            Scope.make.flatMap(_.use[Any](scoped))
          }

          for {
            _       <- testCase.sandbox.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1)))
        },
        test("calls finalizers correctly when using the resource") {
          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(Task.succeed { effects += x }.unit)(_ => Task.succeed { effects += x }.unit)

          val testCase = {
            val scoped: ZIO[Scope, Throwable, Unit] = res(1).toScopedZIO
            Scope.make.flatMap(_.use[Any](scoped))
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 1)))
        },
        test("composing with other scoped should calls finalizers in correct order") {

          val effects = new mutable.ListBuffer[Int]
          def res(x: Int): Resource[Task, Unit] =
            Resource.make(Task.succeed { effects += x }.unit)(_ => Task.succeed { effects += x }.unit)

          def scope(x: Int): ZIO[Scope, Throwable, Unit] =
            ZIO.succeed(effects += x).acquireRelease(ZIO.succeed(effects += x))(ZIO.unit)

          val testCase = {
            val scoped1: ZIO[Scope, Throwable, Unit] = res(1).toScopedZIO
            val scoped2: ZIO[Scope, Throwable, Unit] = scope(2)
            Scope.make.flatMap(_.use[Any](scoped1 *> scoped2))
          }

          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2, 2, 1)))
        }
      ),
      suite("toResource")(
        test("calls finalizers when using resource") {
          val effects = new mutable.ListBuffer[Int]
          def scope(x: Int): ZIO[Scope, Throwable, Unit] =
            ZIO.acquireRelease(ZIO.succeed(effects += x).unit)(_ => ZIO.succeed(effects += x + 1))

          val testCase = ZIO.runtime[Any].flatMap { implicit r =>
            scope(1).toResource.use(_ => ZIO.unit)
          }
          for {
            _       <- testCase
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource fails") {
          val effects = new mutable.ListBuffer[Int]
          def scope(x: Int): ZIO[Scope, Throwable, Unit] =
            ZIO.acquireReleaseExit(ZIO.succeed(effects += x).unit) {
              case (_, Exit.Failure(_)) =>
                ZIO.succeed(effects += x + 1)
              case _ =>
                ZIO.unit
            }

          val testCase = ZIO.runtime[Any].flatMap { implicit r =>
            scope(1).toResource.use(_ => ZIO.fail(new RuntimeException()).unit)
          }
          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        },
        test("calls finalizers when using resource is canceled") {
          val effects = new mutable.ListBuffer[Int]
          def scope(x: Int): ZIO[Scope, Throwable, Unit] =
            ZIO.acquireReleaseExit(ZIO.succeed(effects += x).unit) {
              case (_, e) if e.isInterrupted =>
                ZIO.succeed(effects += x + 1)
              case _ =>
                ZIO.unit
            }

          val testCase = ZIO.runtime[Any].flatMap { implicit r =>
            scope(1).toResource.use(_ => ZIO.interrupt)
          }
          for {
            _       <- testCase.orElse(ZIO.unit)
            effects <- ZIO.succeed(effects.toList)
          } yield assert(effects)(equalTo(List(1, 2)))
        }
      )
    )
}
