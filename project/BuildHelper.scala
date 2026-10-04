import sbt._
import Keys._

import sbtcrossproject.CrossPlugin.autoImport.{ crossProjectPlatform, CrossType, JVMPlatform }
import sbtbuildinfo._
import com.typesafe.tools.mima.plugin.MimaKeys._
import sbtdynver.DynVerPlugin.autoImport.previousStableVersion
import BuildInfoKeys._

object BuildHelper {
  val scalacheckVersion = "1.20.0"

  val testDeps = Seq("org.scalacheck" %% "scalacheck" % scalacheckVersion % Test)

  val Scala212 = "2.12.21"
  val Scala213 = "2.13.18"
  val Scala3   = "3.9.0"

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  )

  private val std2xOptions = Seq(
    "-Xfatal-warnings",
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xsource:3",
    "-P:kind-projector:underscore-placeholders",
    "-Xlint:_,-type-parameter-shadow,-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  private val std3xOptions = Seq(
    "-no-indent",
    "-Xfatal-warnings",
    "-Ykind-projector:underscores"
  )

  val buildInfoSettings = Seq(
    buildInfoKeys    := Seq(
      BuildInfoKey(name),
      BuildInfoKey(version),
      BuildInfoKey(scalaVersion),
      BuildInfoKey(sbtVersion),
      BuildInfoKey(isSnapshot)
    ),
    buildInfoPackage := "zio",
    buildInfoObject  := "BuildInfoInteropCats"
  )

  val buildInfoSettingsInteropTracer = Seq(
    buildInfoKeys    := Seq(
      BuildInfoKey(name),
      BuildInfoKey(version),
      BuildInfoKey(scalaVersion),
      BuildInfoKey(sbtVersion),
      BuildInfoKey(isSnapshot)
    ),
    buildInfoPackage := "zio.internal.stacktracer",
    buildInfoObject  := "BuildInfoInteropTracer"
  )

  val mimaSettings = Seq(
    mimaPreviousArtifacts := previousStableVersion.value.map(organization.value %% moduleName.value % _).toSet
  )

  def optimizerOptions(optimize: Boolean): Seq[String] =
    if (optimize) {
      Seq(
        "-opt:l:inline",
        "-opt-inline-from:zio.interop.**"
      )
    } else Nil

  def extraOptions(scalaVersion: String, optimize: Boolean): Seq[String] =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 3))  =>
        std3xOptions
      case Some((2, 13)) =>
        Seq(
          "-Wextra-implicit",
          "-Wnumeric-widen",
          "-Wunused:_",
          "-Wvalue-discard",
          // `Traverse[Chunk] with Alternative[Chunk]` inherits both the parameterless `compose` of `SemigroupK` and the
          // overloaded `compose(implicit ...)` of the `Functor` family; cats' own instances have the same shape.
          "-Wconf:msg=will be easy to mistake for calls to overloads:s"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case _             => Seq.empty
    }

  val nativeTestInterfaceScheme =
    libraryDependencySchemes += ("org.scala-native" % s"test-interface_${platform.value}" % VersionScheme.Always)
      .cross(CrossVersion.binary)

  def stdSettings(prjName: String) = Seq(
    name                     := s"$prjName",
    crossScalaVersions       := Seq(Scala3, Scala213, Scala212),
    ThisBuild / scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
    scalacOptions ++= {
      // Before Scala 3.8, lazy vals use `sun.misc.Unsafe` by default, which JDK 24+ warns about.
      // The VarHandle-based encoding needs Java 9+ bytecode; ZIO itself targets Java 11.
      if (scalaVersion.value.startsWith("3") && crossProjectPlatform.value == JVMPlatform)
        Seq("-Yfuture-lazy-vals", "-java-output-version:11")
      else Seq.empty
    },
    libraryDependencies ++= testDeps ++ {
      if (CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 2))
        Seq(
          compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.4").cross(CrossVersion.full)),
          // Scala 2 cannot type-check subclasses of `Async` without this `provided` dependency of cats-effect-kernel:
          // https://github.com/typelevel/cats-effect/issues/4693
          ("org.typelevel" %% "scalac-compat-annotation" % "0.1.5" % Provided).platform(Platform.jvm)
        )
      else Seq.empty
    },
    Test / parallelExecution := true,
    nativeTestInterfaceScheme,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings          := true,
    Compile / unmanagedSourceDirectories ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, x)) if x <= 11 =>
          CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.11")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.11"))
        case Some((2, x)) if x >= 12 =>
          CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-2.12+")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-2.12+"))
        case Some((3, 0))            =>
          CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-3")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-3"))
        case _                       => Nil
      }
    },
    Test / unmanagedSourceDirectories ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, x)) if x <= 11 =>
          Seq(file(sourceDirectory.value.getPath + "/test/scala-2.11"))
        case Some((2, x)) if x >= 12 =>
          Seq(
            file(sourceDirectory.value.getPath + "/test/scala-2.12"),
            file(sourceDirectory.value.getPath + "/test/scala-2.12+")
          )
        case _                       => Nil
      }
    }
  )
}
