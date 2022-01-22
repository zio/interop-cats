import BuildHelper._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject

name := "interop-cats"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.dev")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/interop-cats/"), "scm:git:git@github.com:zio/interop-cats.git")
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("testJVM", ";zioInteropCatsTestsJVM/test;zioTestInteropCatsJVM/test;coreOnlyTestJVM/test")
addCommandAlias("testJS", ";zioInteropCatsTestsJS/test;zioTestInteropCatsJS/test;coreOnlyTestJS/test")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate(
    zioInteropCatsJVM,
    zioInteropCatsJS,
    zioInteropCatsTestsJVM,
    zioInteropCatsTestsJS,
    zioTestInteropCatsJVM,
    zioTestInteropCatsJS
  )
  .settings(
    publish / skip := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

resolvers in Global +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

val zioVersion = "2.0.0-RC1+68-2319dbe7-SNAPSHOT"

lazy val zioInteropCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-interop-cats"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %%% "zio"             % zioVersion,
      "dev.zio"       %%% "zio-stacktracer" % zioVersion,
      "dev.zio"       %%% "zio-streams"     % zioVersion,
      "org.typelevel" %%% "cats-effect"     % "2.5.1",
      "org.typelevel" %%% "cats-mtl"        % "1.2.1",
      "co.fs2"        %%% "fs2-core"        % "2.5.6"
    )
  )

lazy val zioInteropCatsJVM = zioInteropCats.jvm.settings(dottySettings)

lazy val zioInteropCatsJS = zioInteropCats.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.3.0" % Test
  )
  .settings(dottySettings)

lazy val zioInteropCatsTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-interop-cats-tests"))
  .dependsOn(zioTestInteropCats)
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats-tests"))
  .settings(buildInfoSettings)
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %%% "zio-test-sbt"         % zioVersion % Test,
      "org.typelevel" %%% "cats-testkit"         % "2.6.1"    % Test,
      "org.typelevel" %%% "cats-effect-laws"     % "2.5.1"    % Test,
      "org.typelevel" %%% "cats-mtl-laws"        % "1.2.1"    % Test,
      "org.typelevel" %%% "discipline-scalatest" % "2.1.5"    % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val zioInteropCatsTestsJVM = zioInteropCatsTests.jvm.settings(dottySettings)

lazy val zioInteropCatsTestsJS = zioInteropCatsTests.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.3.0" % Test
  )
  .settings(dottySettings)

lazy val zioTestInteropCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-test-interop-cats"))
  .dependsOn(zioInteropCats)
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-test-interop-cats"))
  .settings(buildInfoSettings)
  .settings(libraryDependencies += "dev.zio" %%% "zio-test" % zioVersion)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val zioTestInteropCatsJVM = zioTestInteropCats.jvm.settings(dottySettings)

lazy val zioTestInteropCatsJS = zioTestInteropCats.js

lazy val coreOnlyTest = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-only-test"))
  .dependsOn(zioInteropCats)
  .settings(stdSettings("core-only-test"))
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"    % "2.6.1"    % Test,
      "dev.zio"       %%% "zio-test-sbt" % zioVersion % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val coreOnlyTestJVM = coreOnlyTest.jvm.settings(dottySettings)

lazy val coreOnlyTestJS = coreOnlyTest.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.3.0" % Test
  )
  .settings(dottySettings)
