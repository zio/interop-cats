import BuildHelper._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject

name := "interop-cats"

inThisBuild(
  List(
    name          := "interop-cats",
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
addCommandAlias("lint", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("testJVM", ";zioInteropCatsTestsJVM/test;zioTestInteropCatsJVM/test;coreOnlyTestJVM/test")
addCommandAlias("testJS", ";zioInteropCatsTestsJS/test;zioTestInteropCatsJS/test;coreOnlyTestJS/test")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate(
    zioInteropTracerJVM,
    zioInteropTracerJS,
    zioInteropCatsJVM,
    zioInteropCatsJS,
    zioInteropCatsTestsJVM,
    zioInteropCatsTestsJS,
    zioTestInteropCatsJVM,
    zioTestInteropCatsJS,
    docs
  )
  .settings(
    publish / skip := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

val zioVersion                 = "2.1.14"
val catsVersion                = "2.12.0"
val catsEffectVersion          = "3.5.7"
val catsMtlVersion             = "1.5.0"
val disciplineScalaTestVersion = "2.3.0"
val fs2Version                 = "3.11.0"
val scalaJavaTimeVersion       = "2.6.0"

lazy val zioInteropTracer = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-interop-tracer"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-tracer"))
  .settings(dottySettings)
  .settings(buildInfoSettingsInteropTracer)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-stacktracer" % zioVersion
    )
  )

lazy val zioInteropTracerJVM = zioInteropTracer.jvm
lazy val zioInteropTracerJS  = zioInteropTracer.js

lazy val zioInteropCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-interop-cats"))
  .dependsOn(zioInteropTracer)
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats"))
  .settings(buildInfoSettings)
  .settings(dottySettings)
  .settings(
    libraryDependencies ++= {
      val optLibraries0 = List(
        "dev.zio"       %%% "zio-managed"     % zioVersion,
        "dev.zio"       %%% "zio-streams"     % zioVersion,
        "org.typelevel" %%% "cats-effect-std" % catsEffectVersion,
        "org.typelevel" %%% "cats-mtl"        % catsMtlVersion,
        "co.fs2"        %%% "fs2-core"        % fs2Version,
        "co.fs2"        %%% "fs2-io"          % fs2Version
      )
      val optLibraries  = if (scalaVersion.value.startsWith("3")) optLibraries0 else optLibraries0.map(_ % Optional)
      ("dev.zio" %%% "zio" % zioVersion) :: optLibraries
    }
  )

lazy val zioInteropCatsJVM = zioInteropCats.jvm

lazy val zioInteropCatsJS = zioInteropCats.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

lazy val zioInteropCatsTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-interop-cats-tests"))
  .dependsOn(zioTestInteropCats % "test->test;compile->compile")
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-cats-tests"))
  .settings(buildInfoSettings)
  .settings(dottySettings)
  .settings(
    libraryDependencies ++= {
      val optLibraries0 = List(
        "dev.zio"       %%% "zio-managed"     % zioVersion,
        "dev.zio"       %%% "zio-streams"     % zioVersion,
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

lazy val zioInteropCatsTestsJVM = zioInteropCatsTests.jvm

lazy val zioInteropCatsTestsJS = zioInteropCatsTests.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

lazy val zioTestInteropCats = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-test-interop-cats"))
  .dependsOn(zioInteropCats)
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-test-interop-cats"))
  .settings(buildInfoSettings)
  .settings(dottySettings)
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= {
      val optLibraries0 = List(
        "dev.zio"       %%% "zio-managed"     % zioVersion,
        "dev.zio"       %%% "zio-streams"     % zioVersion,
        "dev.zio"       %%% "zio-test"        % zioVersion,
        "org.typelevel" %%% "cats-effect-std" % catsEffectVersion,
        "org.typelevel" %%% "cats-mtl"        % catsMtlVersion,
        "co.fs2"        %%% "fs2-core"        % fs2Version
      )
      val optLibraries  = if (scalaVersion.value.startsWith("3")) optLibraries0 else optLibraries0.map(_ % Optional)
      ("dev.zio" %%% "zio" % zioVersion) :: ("org.typelevel" %%% "cats-core" % catsVersion) :: optLibraries
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

lazy val zioTestInteropCatsJVM = zioTestInteropCats.jvm

lazy val zioTestInteropCatsJS = zioTestInteropCats.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

lazy val coreOnlyTest = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-only-test"))
  .dependsOn(zioInteropCats)
  .settings(stdSettings("core-only-test"))
  .settings(dottySettings)
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"    % catsVersion,
      "dev.zio"       %%% "zio-managed"  % zioVersion,
      "dev.zio"       %%% "zio-test-sbt" % zioVersion
    ).map(_ % Test)
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val coreOnlyTestJVM = coreOnlyTest.jvm

lazy val coreOnlyTestJS = coreOnlyTest.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

lazy val docs = project
  .in(file("zio-interop-cats-docs"))
  .settings(
    projectName                                := "ZIO Interop Cats",
    mainModuleName                             := (zioInteropCatsJVM / moduleName).value,
    projectStage                               := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects()
  )
  .enablePlugins(WebsitePlugin)
