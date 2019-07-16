import sbtcrossproject.CrossPlugin.autoImport.crossProject
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import ScalazBuild._

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
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher,
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/interop-cats/"), "scm:git:git@github.com:zio/interop-cats.git")
    )
  )
)

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
      "dev.zio"       %%% "zio"                  % "1.0.0-RC10-1",
      "org.typelevel" %%% "cats-effect"          % "2.0.0-M4" % Optional,
      "org.typelevel" %%% "cats-mtl-core"        % "0.6.0" % Optional,
      "co.fs2"        %%% "fs2-core"             % "1.1.0-M1" % Test,
      "dev.zio"       %%% "zio"                  % "1.0.0-RC10-1" % Test classifier "tests",
      "org.specs2"    %%% "specs2-core"          % "4.6.0" % Test,
      "org.specs2"    %%% "specs2-scalacheck"    % "4.6.0" % Test,
      "org.specs2"    %%% "specs2-matcher-extra" % "4.6.0" % Test
    )
  )

lazy val interopCatsJVM = interopCats.jvm
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect-laws"           % "2.0.0-M4" % Test,
      "org.typelevel"              %% "cats-testkit"               % "2.0.0-M4" % Test,
      "org.typelevel"              %% "cats-mtl-laws"              % "0.6.0"    % Test,
      "com.github.alexarchambault" %% s"scalacheck-shapeless_1.14" % "1.2.3"    % Test
    )
  )

lazy val interopCatsJS = interopCats.js
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % "0.2.5" % Test
  )
