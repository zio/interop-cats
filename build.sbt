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
addCommandAlias("testJVM", ";interopCatsJVM/test;coreOnlyTestJVM/test")
addCommandAlias("testJS", ";interopCatsJS/test;coreOnlyTestJS/test")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate(interopCatsJVM, interopCatsJS)
  .settings(
    skip in publish := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

val zioVersion = "1.0.0-RC20"
lazy val interopCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-cats"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %%% "zio"                  % zioVersion,
      "dev.zio"       %%% "zio-streams"          % zioVersion % Optional,
      "dev.zio"       %%% "zio-test"             % zioVersion % Optional,
      "org.typelevel" %%% "cats-effect"          % "2.1.3" % Optional,
      "org.typelevel" %%% "cats-mtl-core"        % "0.7.1" % Optional,
      "co.fs2"        %%% "fs2-core"             % "2.4.1" % Test,
      "dev.zio"       %%% "zio-test-sbt"         % zioVersion % Test,
      "org.specs2"    %%% "specs2-core"          % "4.9.4" % Test,
      "org.specs2"    %%% "specs2-scalacheck"    % "4.9.4" % Test,
      "org.specs2"    %%% "specs2-matcher-extra" % "4.9.4" % Test,
      "org.typelevel" %%% "cats-testkit"         % "2.1.1" % Test,
      "org.typelevel" %%% "cats-effect-laws"     % "2.1.3" % Test,
      "org.typelevel" %%% "cats-mtl-laws"        % "0.7.1" % Test,
      "org.typelevel" %%% "discipline-scalatest" % "1.0.1" % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val interopCatsJVM = interopCats.jvm
lazy val interopCatsJS = interopCats.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
  )

lazy val coreOnlyTest = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-only-test"))
  .dependsOn(interopCats)
  .settings(stdSettings("core-only-test"))
  .settings(skip in publish := true)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"    % "2.1.1"    % Test,
      "dev.zio"       %%% "zio-test-sbt" % zioVersion % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val coreOnlyTestJVM = coreOnlyTest.jvm
lazy val coreOnlyTestJS = coreOnlyTest.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
  )
