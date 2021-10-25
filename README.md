# Interop Cats Effect

[![Project stage][Stage]][Stage-Page]
![CI][ci-badge]
[![Releases][Badge-SonatypeReleases]][Link-SonatypeReleases]
[![Snapshots][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

This library provides instances required by Cats Effect.

## Installation
```sbt
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "<check-badge-for-latest-version>"
```

## `ZIO` Cats Effect 3 instances

**ZIO** integrates with Typelevel libraries by providing an instance of `Concurrent`, `Temporal` and `Async` for `Task` 
as required, for instance, by `fs2`, `doobie` and `http4s`.

For convenience, we have defined an alias as follows:

```scala
type Task[A] = ZIO[Any, Throwable, A]
```

Therefore, we provide Cats Effect instances based on this specific datatype.

## `Concurrent`

In order to get a `Concurrent[Task]` or `Concurrent[RIO[R, *]]` (note `*` is kind-projector notation) we need an 
implicit `Runtime[R]` in scope. The easiest way to get it is using `ZIO.runtime`:

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceConcurrentForTaskExample = 
  ZIO.runtime.flatMap { implicit r: Runtime[Any] =>
    // the presence of a runtime allows you to summon Cats Effect Typeclasses
    val F: cats.effect.Concurrent[Task] = implicitly
    F.racePair(F.unit, F.unit)
  }
```

## `Temporal`

`Temporal` requires a `Runtime` with `Has[Clock]`.

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceTemporal =
  ZIO.runtime.flatMap { implicit r: Runtime[Has[Clock]] =>
    val F: cats.effect.Temporal[Task] = implicitly
    F.sleep(1.second) *> F.unit
  }
```

## `Async`

Similar to the other examples, we require a `Runtime` with the `Has[Clock]` layer.

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceAsync =
  ZIO.runtime.flatMap { implicit r: Runtime[Has[Clock]] =>
    val F: cats.effect.Async[Task] = implicitly
    F.racePair(F.unit, F.sleep(1.second) *> F.unit)
  }
```

## Other typeclasses

There are many other typeclasses and useful conversions that this library provides implementations for:
* See `zio/interop/cats.scala` file to see all available typeclass implementations for the Cats Effect 3 typeclasses
* See `zio/stream/interop/cats.scala` for ZStream typeclass implementations
* See `zio/stream/interop/FS2StreamSyntax.scala` for FS2 <-> ZStream conversions

## Easier imports (at a cost)

In the examples above, we had to bring the `Runtime[Has[Clock]]` via the `ZIO.runtime` combinator. This may
not be ideal since everywhere you use these typeclasses, you will now be required to feed in the `Runtime`. 
For example, with FS2: 

```scala
def example(implicit rts: Runtime[Has[Clock]]): Task[Unit] =
  fs2.Stream
    .awakeDelay[Task](10.seconds) // this type annotation is mandatory
    .evalTap(in => cats.effect.std.Console.make[Task].println(s"Hello $in"))
    .compile
    .drain
```

Rather than requiring the runtime implicit, we can add an import (if we don't mind depending on `Runtime.default`):
```scala
import zio.interop.catz._
import zio.interop.catz.implicits._

val example: Task[Unit] =
  fs2.Stream
    .awakeDelay[Task](10.seconds)
    .evalTap(in => cats.effect.std.Console.make[Task].println(s"Hello $in"))
    .compile
    .drain
```

The major downside to doing this is you rely on live implementations of `Clock` to summon instances which 
makes testing much more difficult for any Cats Effect code that you use. 

### cats-core

If you only need instances for `cats-core` typeclasses, not `cats-effect` import `zio.interop.catz.core._`:

```scala
import zio.interop.catz.core._
```

Note that this library only has an `Optional` dependency on cats-effect – if you or your libraries don't depend on it, this library will not add it to the classpath.

### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances (`cats.effect.Async` and `cats.effect.Temporal`):

```scala
import zio._
import zio.interop.catz._
import doobie._
import doobie.implicits._
import zio.blocking.Blocking
import zio.clock.Clock
import scala.concurrent.duration._

object DoobieH2Example extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    ZIO.runtime.flatMap { implicit r: Runtime[Has[Clock]] =>
      val xa: Transactor[Task] =
        Transactor.fromDriverManager[Task]("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "")

      sql"SELECT 42"
        .query[Int]
        .stream
        .transact(xa)
        .delayBy(1.second)
        .evalTap(i => ZIO.succeedBlocking(println(s"Data $i"))))
        .compile
        .drain
        .exitCode
    }
}
```

[ci-badge]: https://github.com/zio/interop-cats/workflows/CI/badge.svg
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-interop-cats_2.12/
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-interop-cats_2.12.svg
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-interop-cats_2.12/
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-interop-cats_2.12.svg
[Stage]: https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg
[Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages
