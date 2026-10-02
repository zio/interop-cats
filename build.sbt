import BuildHelper._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import zio.sbt.WebsitePlugin.publishHashverToNpmTask

name := "interop-cats"

inThisBuild(
  List(
    name          := "interop-cats",
    organization  := "dev.zio",
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

ThisBuild / ciTargetJavaVersions := Seq("11", "17", "21", "25")

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "mimaCheck",
  ";+zioInteropTracerJVM/mimaReportBinaryIssues;+zioInteropCatsJVM/mimaReportBinaryIssues;+zioTestInteropCatsJVM/mimaReportBinaryIssues"
)
addCommandAlias("lint", ";all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck;mimaCheck")
addCommandAlias("testJVM", ";zioInteropCatsTestsJVM/test;zioTestInteropCatsJVM/test;coreOnlyTestJVM/test")
addCommandAlias("testJS", ";zioInteropCatsTestsJS/test;zioTestInteropCatsJS/test;coreOnlyTestJS/test")
addCommandAlias("testNative", ";zioInteropCatsTestsNative/test;zioTestInteropCatsNative/test;coreOnlyTestNative/test")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .aggregate(
    zioInteropTracerJVM,
    zioInteropTracerJS,
    zioInteropTracerNative,
    zioInteropCatsJVM,
    zioInteropCatsJS,
    zioInteropCatsNative,
    zioInteropCatsTestsJVM,
    zioInteropCatsTestsJS,
    zioInteropCatsTestsNative,
    zioTestInteropCatsJVM,
    zioTestInteropCatsJS,
    zioTestInteropCatsNative,
    zioInteropCatsLawsJVM,
    zioInteropCatsLawsJS,
    zioInteropCatsLawsNative,
    docs
  )
  .settings(
    publish / skip := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )

val zioVersion                 = "2.1.23"
val catsVersion                = "2.13.0"
val catsEffectVersion          = "3.7.1"
val catsMtlVersion             = "1.7.0"
val disciplineScalaTestVersion = "2.3.0"
val fs2Version                 = "3.14.0"
val scalaJavaTimeVersion       = "2.6.0"

lazy val zioInteropTracer       = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-interop-tracer"))
  .enablePlugins(BuildInfoPlugin)
  .settings(BuildHelper.stdSettings("zio-interop-tracer"))
  .settings(buildInfoSettingsInteropTracer)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-stacktracer" % zioVersion
    )
  )
lazy val zioInteropTracerJVM    = zioInteropTracer.jvm
  .settings(mimaSettings)
lazy val zioInteropTracerJS     = zioInteropTracer.js
lazy val zioInteropTracerNative = zioInteropTracer.native

lazy val zioInteropCats       = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-interop-cats"))
  .dependsOn(zioInteropTracer)
  .enablePlugins(BuildInfoPlugin)
  .settings(BuildHelper.stdSettings("zio-interop-cats"))
  .settings(BuildHelper.buildInfoSettings)
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
lazy val zioInteropCatsJVM    = zioInteropCats.jvm
  .settings(mimaSettings)
lazy val zioInteropCatsJS     = zioInteropCats.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)
lazy val zioInteropCatsNative = zioInteropCats.native
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

// zio-test integration with cats
lazy val zioTestInteropCats       = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-test-interop-cats"))
  .dependsOn(zioInteropCats)
  .enablePlugins(BuildInfoPlugin)
  .settings(BuildHelper.stdSettings("zio-test-interop-cats"))
  .settings(BuildHelper.buildInfoSettings)
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
lazy val zioTestInteropCatsJVM    = zioTestInteropCats.jvm
  .settings(mimaSettings)
lazy val zioTestInteropCatsJS     = zioTestInteropCats.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)
lazy val zioTestInteropCatsNative = zioTestInteropCats.native
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

// Arbitrary/Cogen/Eq instances for ZIO data types, for use in cats/cats-effect law tests
lazy val zioInteropCatsLaws       = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-interop-cats-laws"))
  .dependsOn(zioInteropCats)
  .settings(BuildHelper.stdSettings("zio-interop-cats-laws"))
  .settings(
    // scalacheck is a compile dependency of this module
    libraryDependencies --= BuildHelper.testDeps,
    libraryDependencies ++= Seq(
      "dev.zio"        %%% "zio"                 % zioVersion,
      "dev.zio"        %%% "zio-managed"         % zioVersion,
      "dev.zio"        %%% "zio-streams"         % zioVersion,
      "org.typelevel"  %%% "cats-core"           % catsVersion,
      "org.typelevel"  %%% "cats-effect"         % catsEffectVersion,
      "org.typelevel"  %%% "cats-effect-testkit" % catsEffectVersion,
      "org.scalacheck" %%% "scalacheck"          % scalacheckVersion
    )
  )
lazy val zioInteropCatsLawsJVM    = zioInteropCatsLaws.jvm
lazy val zioInteropCatsLawsJS     = zioInteropCatsLaws.js
lazy val zioInteropCatsLawsNative = zioInteropCatsLaws.native

// test artifacts

val notPublished = publish / skip := true

lazy val zioInteropCatsTests       = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("zio-interop-cats-tests"))
  .dependsOn(zioTestInteropCats % "test->test;compile->compile", zioInteropCatsLaws % "test->compile")
  .enablePlugins(BuildInfoPlugin)
  .settings(BuildHelper.stdSettings("zio-interop-cats-tests"))
  .settings(BuildHelper.buildInfoSettings)
  .settings(notPublished)
  .settings(
    publish / skip := true,
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
lazy val zioInteropCatsTestsJVM    = zioInteropCatsTests.jvm
lazy val zioInteropCatsTestsJS     = zioInteropCatsTests.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)
lazy val zioInteropCatsTestsNative = zioInteropCatsTests.native
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

lazy val coreOnlyTest       = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("core-only-test"))
  .dependsOn(zioInteropCats)
  .settings(BuildHelper.stdSettings("core-only-test"))
  .settings(notPublished)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"    % catsVersion,
      "dev.zio"       %%% "zio-managed"  % zioVersion,
      "dev.zio"       %%% "zio-test-sbt" % zioVersion
    ).map(_ % Test)
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
lazy val coreOnlyTestJVM    = coreOnlyTest.jvm
lazy val coreOnlyTestJS     = coreOnlyTest.js
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)
lazy val coreOnlyTestNative = coreOnlyTest.native
  .settings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test)

// doc website

lazy val docs = project
  .in(file("zio-interop-cats-docs"))
  .settings(notPublished)
  .settings(
    moduleName                                 := "zio-interop-cats-docs",
    projectName                                := "ZIO Interop Cats",
    mainModuleName                             := (zioInteropCatsJVM / moduleName).value,
    projectStage                               := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(),
    publishToNpm                               := publishHashverToNpmTask.value
  )
  .enablePlugins(WebsitePlugin)
