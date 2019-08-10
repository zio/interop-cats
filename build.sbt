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
addCommandAlias("testJVM", ";interopCatsJVM/test;interopCatsEffectJVM/test")
addCommandAlias("testJS", ";interopCatsJS/test;interopCatsEffectJS/test")

lazy val versionOf = new {
  val cats                = "2.0.0-M4"
  val catsEffect          = "2.0.0-M5"
  val catsMtl             = "0.6.0"
  val fs2                 = "1.1.0-M1"
  val kindProjector       = "0.10.3"
  val scalacheckShapeless = "1.2.3"
  val scalajsJavaTime     = "0.2.5"
  val spec2               = "4.7.0"
  val zio                 = "1.0.0-RC11-1"
}

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate(interopCatsJVM, interopCatsJS, interopCatsEffectJVM, interopCatsEffectJS)
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
      "dev.zio"       %%% "zio"                  % versionOf.zio,
      "org.typelevel" %%% "cats-effect"          % versionOf.catsEffect % Optional,
      "org.typelevel" %%% "cats-mtl-core"        % versionOf.catsMtl % Optional,
      "co.fs2"        %%% "fs2-core"             % versionOf.fs2 % Test,
      "dev.zio"       %%% "core-tests"           % versionOf.zio % Test classifier "tests",
      "org.specs2"    %%% "specs2-core"          % versionOf.spec2 % Test,
      "org.specs2"    %%% "specs2-scalacheck"    % versionOf.spec2 % Test,
      "org.specs2"    %%% "specs2-matcher-extra" % versionOf.spec2 % Test
    )
  )

lazy val interopCatsJVM = interopCats.jvm
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect-laws"           % versionOf.catsEffect          % Test,
      "org.typelevel"              %% "cats-testkit"               % versionOf.cats                % Test,
      "org.typelevel"              %% "cats-mtl-laws"              % versionOf.catsMtl             % Test,
      "com.github.alexarchambault" %% s"scalacheck-shapeless_1.14" % versionOf.scalacheckShapeless % Test
    )
  )

lazy val interopCatsJS = interopCats.js
  .settings(
    libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % versionOf.scalajsJavaTime % Test
  )

lazy val interopCatsEffect =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("interop-cats-effect"))
    .settings(stdSettings("zio-interop-cats-effect"))
    .settings(buildInfoSettings)
    .settings(
      skip in publish := true,
      libraryDependencies ++= Seq(
        "dev.zio"       %%% "zio"         % versionOf.zio,
        "org.typelevel" %%% "cats-effect" % versionOf.catsEffect % Optional,
        "org.specs2"    %%% "specs2-core" % versionOf.spec2 % Test,
        compilerPlugin("org.typelevel" %% "kind-projector" % versionOf.kindProjector)
      )
    )

lazy val interopCatsEffectJVM =
  interopCatsEffect.jvm.settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %%% "core-tests"  % versionOf.zio  % Test classifier "tests",
      "org.typelevel" %% "cats-testkit" % versionOf.cats % Test
    )
  )
lazy val interopCatsEffectJS = interopCatsEffect.js
