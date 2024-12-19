import sbt._
import Keys._

import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {
  val testDeps = Seq("org.scalacheck" %% "scalacheck" % "1.18.1" % Test)

  val Scala212 = "2.12.20"
  val Scala213 = "2.13.15"
  val Scala3   = "3.3.4"

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
    "-Xfatal-warnings",
    "-Ykind-projector:underscores"
  )

  val buildInfoSettings = Seq(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "zio",
    buildInfoObject  := "BuildInfoInteropCats"
  )

  val buildInfoSettingsInteropTracer = Seq(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "zio.internal.stacktracer",
    buildInfoObject  := "BuildInfoInteropTracer"
  )

  def optimizerOptions(optimize: Boolean) =
    if (optimize) {
      Seq(
        "-opt:l:inline",
        "-opt-inline-from:zio.interop.**"
      )
    } else Nil

  def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 3))  =>
        std3xOptions
      case Some((2, 13)) =>
        Seq(
          "-Wextra-implicit",
          "-Wnumeric-widen",
          "-Wunused:_",
          "-Wvalue-discard"
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

  def stdSettings(prjName: String) = Seq(
    name                     := s"$prjName",
    crossScalaVersions       := Seq(Scala213, Scala212),
    ThisBuild / scalaVersion := crossScalaVersions.value.head,
    scalacOptions ++= stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
    libraryDependencies ++= testDeps ++ {
      if (isDotty(scalaVersion.value)) Seq.empty
      else Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3") cross CrossVersion.full)
    },
    Test / parallelExecution := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings          := true,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
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

  def isDotty(scalaVersion: String): Boolean =
    CrossVersion.partialVersion(scalaVersion).forall { case (major, _) =>
      major >= 3
    }

  val dottySettings = Seq(
    crossScalaVersions += Scala3,
    scalacOptions ++= {
      if (isDotty(scalaVersion.value)) Seq("-noindent")
      else Seq()
    },
    scalacOptions --= {
      if (isDotty(scalaVersion.value)) Seq("-Xfatal-warnings")
      else Seq()
    },
    Compile / doc / sources  := {
      val old = (Compile / doc / sources).value
      if (isDotty(scalaVersion.value)) Nil
      else {
        old
      }
    },
    Test / parallelExecution := {
      val old = (Test / parallelExecution).value
      if (isDotty(scalaVersion.value)) {
        false
      } else {
        old
      }
    }
  )
}
