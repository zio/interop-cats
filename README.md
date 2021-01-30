# Interop Cats Effect

![CI][ci-badge]
[![Releases][Badge-SonatypeReleases]][Link-SonatypeReleases]
[![Snapshots][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

This library provides instances required by Cats Effect.

## `ZIO` Cats Effect instances

**ZIO** integrates with Typelevel libraries by providing an instance of `ConcurrentEffect` for `IO` as required, for instance, by `fs2`, `doobie` and `http4s`. Actually, I lied a little bit, it is not possible to implement `ConcurrentEffect` for any error type since `ConcurrentEffect` extends `MonadError` of `Throwable`.

For convenience we have defined an alias as follow:

```scala
  type Task[A] = IO[Throwable, A]
```

Therefore, we provide an instance of `ConcurrentEffect[Task]`.

## ConcurrentEffect

In order to get a `ConcurrentEffect[Task]` or `ConcurrentEffect[RIO[R, *]]` we need an implicit `Runtime[R]` in scope. The easiest way to get it is using `ZIO.runtime`:

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def getCE = {
  ZIO.runtime.map { implicit r: Runtime[Any] =>
    val F: ConcurrentEffect[Task] = implicitly
  }
}
```

`Task.concurrentEffectWith` method can automate this pattern:

```scala
import cats.effect._
import zio._
import zio.interop.catz._

def fork = {
  Task.concurrentEffectWith { implicit CE =>
    CE.start(Task(println("Started task")))
  }
}
```

### Timer

In order to get a `cats.effect.Timer[Task]` instance we need an extra import:

```scala
import zio.interop.catz.implicits._
```

The reason it is not provided by the default "interop" import is that it makes testing programs that require timing capabilities hard so an extra import wherever needed makes reasoning about it much easier.

### cats-core

If you only need instances for `cats-core` typeclasses, not `cats-effect` import `zio.interop.catz.core._`:

````scala
import zio.interop.catz.core._
````

Note that this library only has an `Optional` dependency on cats-effect – if you or your libraries don't depend on it, this library will not add it to the classpath.

### Example

The following example shows how to use ZIO with Doobie (a library for JDBC access) and FS2 (a streaming library), which both rely on Cats Effect instances:

```scala
import doobie.imports._
import fs2.Stream
import zio.Task
import zio.interop.catz._

val xa: Transactor[Task] = Transactor.fromDriverManager[Task](...)

def loadUsers: Stream[Task, User] =
  sql"""SELECT * FROM users""".query[User].stream.transact(xa)

val allUsers: List[User] = unsafeRun(loadUsers.compile.toList)
```

[ci-badge]: https://github.com/zio/interop-cats/workflows/CI/badge.svg
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-interop-cats_2.12/
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-interop-cats_2.12.svg
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-interop-cats_2.12/
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-interop-cats_2.12.svg
