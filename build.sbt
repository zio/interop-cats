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
      "dev.zio"       %%% "zio"                  % "1.0.0-RC8-12",
      "org.typelevel" %%% "cats-effect"          % "1.3.1" % Optional,
      "org.typelevel" %%% "cats-mtl-core"        % "0.5.0" % Optional,
      "co.fs2"        %%% "fs2-core"             % "1.0.5" % Test,
      "dev.zio"       %%% "zio"                  % "1.0.0-RC8-12" % Test classifier "tests",
      "org.specs2"    %%% "specs2-core"          % "4.6.0" % Test,
      "org.specs2"    %%% "specs2-scalacheck"    % "4.6.0" % Test,
      "org.specs2"    %%% "specs2-matcher-extra" % "4.6.0" % Test
    )
  )

val CatsScalaCheckVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      "1.13"
    case _ =>
      "1.14"
  }
}

val ScalaCheckVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      "1.13.5"
    case _ =>
      "1.14.0"
  }
}

def majorMinor(version: String) = version.split('.').take(2).mkString(".")

val CatsScalaCheckShapelessVersion = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      "1.1.8"
    case _ =>
      "1.2.0-1+7-a4ed6f38-SNAPSHOT" // TODO: Stable version
  }
}

// Below is for the cats law spec
// Separated due to binary incompatibility in scalacheck 1.13 vs 1.14
// TODO remove it when https://github.com/typelevel/discipline/issues/52 is closed
lazy val interopCatsJVM = interopCats.jvm
  .settings(
    // TODO: Remove once scalacheck-shapeless has a stable version for 2.13.0-M5
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect-laws"                                                 % "1.3.1"                              % Test,
      "org.typelevel"              %% "cats-testkit"                                                     % "1.6.1"                              % Test,
      "org.typelevel"              %% "cats-mtl-laws"                                                    % "0.5.0"                              % Test,
      "com.github.alexarchambault" %% s"scalacheck-shapeless_${majorMinor(CatsScalaCheckVersion.value)}" % CatsScalaCheckShapelessVersion.value % Test
    ),
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % ScalaCheckVersion.value % Test
  )

lazy val interopCatsJS = interopCats.js
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % "0.2.5" % Test
  )
