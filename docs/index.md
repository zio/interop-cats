---
id: index
title: "ZIO 2.x Interoperation with Cats 3.x"
sidebar_label: "ZIO 2.x Interop Cats 3.x"
---

## Installation

```sbt
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "<latest-version>"
```

## `ZIO` Cats Effect 3 instances

**ZIO** integrates with Typelevel libraries by providing an instance of `Concurrent`, `Temporal` and `Async` for `Task`
as required, for instance, by `fs2`, `doobie` and `http4s`.

For convenience, the ZIO library defines an alias as follows:

```scala
type Task[A] = ZIO[Any, Throwable, A]
```

Therefore, we provide Cats Effect instances based on this specific datatype.

## `Concurrent`

In order to get a `Concurrent[Task]` or `Concurrent[RIO[R, *]]` (note `*` is kind-projector notation) we need to import `zio.interop.catz._`:

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceConcurrentForTaskExample = {
  val F: cats.effect.Concurrent[Task] = implicitly
  F.racePair(F.unit, F.unit)
}
```

## `Temporal`

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceTemporal = {
  val F: cats.effect.Temporal[Task] = implicitly
  F.sleep(1.second) *> F.unit
}
```

## `Async`

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def ceAsync = {
  val F: cats.effect.Async[Task] = implicitly
  F.racePair(F.unit, F.sleep(1.second) *> F.unit)
}
```

## Recovering from defects with `catz.autocatch`

The default instances capture exceptions thrown outside of `Sync#delay`, for example inside `map` or `flatMap`, as ZIO
defects (`ZIO.die`). Cats Effect methods such as `handleErrorWith`, `attempt` or `recover` cannot recover from defects:

```scala
import cats.effect._
import cats.syntax.all._
import zio._
import zio.interop.catz._

// dies with the exception instead of recovering
val dies: Task[Int] = Async[Task].unit.map(_ => throw new RuntimeException("boom")).handleError(_ => 1)
```

Code written against Cats Effect typeclasses often expects such exceptions to be recoverable, as they are with
`cats.effect.IO`. For such code, `zio.interop.catz.autocatch._` provides alternative `Async`, `Temporal` and `Concurrent`
instances for `RIO[R, *]` that make defects recoverable:

```scala
import cats.effect._
import cats.syntax.all._
import zio._
import zio.interop.catz.autocatch._

// succeeds with 1
val recovers: Task[Int] = Async[Task].unit.map(_ => throw new RuntimeException("boom")).handleError(_ => 1)
```

With these instances:
* `handleErrorWith`, `recoverWith`, `attempt`, `adaptError` and the methods derived from them recover from defects. A
  single defect is passed to the handler as is, multiple defects are passed as a `FiberFailure` holding the whole
  `Cause`. That is the same `Throwable` that `guaranteeCase` and `Fiber#join` report as `Outcome.Errored`.
* A typed failure takes precedence over defects in the same `Cause`, as with the default instances.
* A defect is not recovered if the fiber is being interrupted.
* A `Cause` without defects is recovered from the same way as by the default instances.
* `unlessA`, `raiseUnless` and `fromOption` suspend their by-name arguments, so that exceptions thrown by them are
  recoverable too.

Exceptions thrown while an effect is being constructed, before it is passed to a typeclass method, for example by the
argument of `pure`, cannot be captured by any instance.

These instances replace `zio.interop.catz._`, do not import both in the same scope. Instances for cats-core typeclasses,
such as `Parallel`, can be imported alongside:

```scala
import zio.interop.catz.core._
import zio.interop.catz.autocatch._
```

## Other typeclasses

There are many other typeclasses and useful conversions that this library provides implementations for:
* See `zio/interop/cats.scala` file to see all available typeclass implementations for the Cats Effect 3 typeclasses
* See `zio/stream/interop/cats.scala` for ZStream typeclass implementations
* See `zio/stream/interop/FS2StreamSyntax.scala` for FS2 <-> ZStream conversions


### cats-core

If you only need instances for `cats-core` typeclasses, not `cats-effect` import `zio.interop.catz.core._`:

```scala
import zio.interop.catz.core._
```

Note that this library only has an `Optional` dependency on cats-effect – if you or your libraries don't depend on it, this library will not add it to the classpath.

### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances (`cats.effect.Async` and `cats.effect.Temporal`):

```scala
import zio.{durationInt as _, *}
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import scala.concurrent.duration.*

object Example extends ZIOAppDefault:
  val run = {
    val xa: Transactor[Task] =
      Transactor.fromDriverManager[Task]("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "", None)

    sql"SELECT 42"
      .query[Int]
      .stream
      .transact(xa)
      .delayBy(1.second)
      .evalTap(i => Console.printLine(i))
      .compile
      .drain
      .exitCode
  }
```

## Links

- [Guide: How to Interop with Cats Effect?](https://zio.dev/guides/interop/with-cats-effect)
