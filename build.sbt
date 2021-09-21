import BuildHelper._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject

name := "interop-cats"

inThisBuild(
  List(
    organization  := "dev.zio",
    homepage      := Some(url("https://zio.dev")),
    licenses      := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers    := List(
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
    scmInfo       := Some(
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
    publish / skip := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

val zioVersion                 = "1.0.12"
val catsVersion                = "2.6.1"
val catsEffectVersion          = "3.1.1"
val catsMtlVersion             = "1.2.1"
val disciplineScalaTestVersion = "2.1.5"
val fs2Version                 = "3.0.6"
val scalaJavaTimeVersion       = "2.3.0"

lazy val interopCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop-cats"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= {
      val optLibraries0 = List(
        "dev.zio"       %%% "zio-streams"     % zioVersion,
        "dev.zio"       %%% "zio-test"        % zioVersion,
        "org.typelevel" %%% "cats-effect-std" % catsEffectVersion,
        "org.typelevel" %%% "cats-mtl"        % catsMtlVersion,
        "co.fs2"        %%% "fs2-core"        % fs2Version
      )
      val optLibraries  = if (scalaVersion.value.startsWith("3")) optLibraries0 else optLibraries0.map(_ % Optional)
      ("dev.zio" %%% "zio" % zioVersion) :: optLibraries
    },
    libraryDependencies ++= Seq(
      "dev.zio"       %%% "zio-test-sbt"         % zioVersion,
      "org.typelevel" %%% "cats-testkit"         % catsVersion,
      "org.typelevel" %%% "cats-effect-laws"     % catsEffectVersion,
      "org.typelevel" %%% "cats-effect-testkit"  % catsEffectVersion,
      "org.typelevel" %%% "cats-mtl-laws"        % catsMtlVersion,
      "org.typelevel" %%% "discipline-scalatest" % disciplineScalaTestVersion
    ).map(_ % Test)
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val interopCatsJVM = interopCats.jvm.settings(dottySettings)

lazy val interopCatsJS = interopCats.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

lazy val coreOnlyTest  = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-only-test"))
  .dependsOn(interopCats)
  .settings(stdSettings("core-only-test"))
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"    % catsVersion,
      "dev.zio"       %%% "zio-test-sbt" % zioVersion
    ).map(_ % Test)
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val coreOnlyTestJVM = coreOnlyTest.jvm.settings(dottySettings)

lazy val coreOnlyTestJS = coreOnlyTest.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)
