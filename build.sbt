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

val zioVersion = "1.0.3"
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
      "org.typelevel" %%% "cats-effect"          % "2.3.1" % Optional,
      "org.typelevel" %%% "cats-mtl"             % "1.0.0" % Optional,
      "co.fs2"        %%% "fs2-core"             % "2.4.4" % Optional,
      "dev.zio"       %%% "zio-test-sbt"         % zioVersion % Test,
      "org.typelevel" %%% "cats-testkit"         % "2.2.0" % Test,
      "org.typelevel" %%% "cats-effect-laws"     % "2.3.1" % Test,
      "org.typelevel" %%% "cats-mtl-laws"        % "1.0.0" % Test,
      "org.typelevel" %%% "discipline-scalatest" % "2.0.1" % Test
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
      "org.typelevel" %%% "cats-core"    % "2.2.0"    % Test,
      "dev.zio"       %%% "zio-test-sbt" % zioVersion % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val coreOnlyTestJVM = coreOnlyTest.jvm
lazy val coreOnlyTestJS = coreOnlyTest.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
  )
