import sbtcrossproject.CrossPlugin.autoImport.crossProject
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import BuildHelper._

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

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("testJVM", ";interopCatsJVM/test")
addCommandAlias("testJS", ";interopCatsJS/test")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate(interopCatsJVM, interopCatsJS)
  .settings(
    skip in publish := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

lazy val interopCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-cats"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %%% "zio"                  % "1.0.0-RC14",
      "dev.zio"       %%% "zio-test"             % "1.0.0-RC14",
      "org.typelevel" %%% "cats-effect"          % "2.0.0" % Optional,
      "org.typelevel" %%% "cats-mtl-core"        % "0.7.0" % Optional,
      "co.fs2"        %%% "fs2-core"             % "2.0.1" % Test,
      "dev.zio"       %%% "zio-test-sbt"         % "1.0.0-RC14" % Test,
      "org.specs2"    %%% "specs2-core"          % "4.7.1" % Test,
      "org.specs2"    %%% "specs2-scalacheck"    % "4.7.1" % Test,
      "org.specs2"    %%% "specs2-matcher-extra" % "4.7.1" % Test,
      "org.typelevel" %%% "cats-testkit"         % "2.0.0" % Test,
      "org.typelevel" %%% "cats-effect-laws"     % "2.0.0" % Test,
      "org.typelevel" %%% "cats-mtl-laws"        % "0.7.0" % Test,
      "org.typelevel" %%% "discipline-scalatest" % "1.0.0-RC1" % Test
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val interopCatsJVM = interopCats.jvm
lazy val interopCatsJS = interopCats.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test
  )
