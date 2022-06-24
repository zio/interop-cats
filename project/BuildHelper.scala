import sbt._
import Keys._

import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {
  val testDeps = Seq("org.scalacheck" %% "scalacheck" % "1.15.4" % Test)

  val Scala212 = "2.12.15"
  val Scala213 = "2.13.8"
  val Scala3   = "3.1.2"

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
    "-Xsource:2.13",
    "-Xlint:_,-type-parameter-shadow,-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  private val std3xOptions = Seq(
    "-Xfatal-warnings",
    "-Ykind-projector"
  )

  val buildInfoSettings = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "zio",
    buildInfoObject := "BuildInfoInteropCats"
  )

  val optimizerOptions = {
    Seq(
      "-opt:l:inline",
      "-opt-inline-from:zio.interop.**"
    )
  }

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 1)) =>
        std3xOptions
      case Some((2, 13)) =>
        Seq(
          "-Wextra-implicit",
          "-Wnumeric-widen",
          "-Wunused:_",
          "-Wvalue-discard"
        ) ++ std2xOptions ++ optimizerOptions
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
        ) ++ std2xOptions ++ optimizerOptions
      case _ => Seq.empty
    }

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq(Scala213, Scala212),
    ThisBuild / scalaVersion := crossScalaVersions.value.head,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= testDeps ++ {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq.empty
        case _ =>
          Seq(
            compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2") cross CrossVersion.full
          )
      }
    },
    Test / parallelExecution := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
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
        case Some((3, 0)) =>
          CrossType.Full.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + "-3")) ++
            CrossType.Full.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + "-3"))
        case _ => Nil
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
        case _ => Nil
      }
    }
  )

  val dottySettings = {
    def isScala3(scalaVer: String) = CrossVersion.partialVersion(scalaVer) match {
      case Some((3, _)) => true
      case _            => false
    }
    Seq(
      crossScalaVersions += Scala3,
      scalacOptions ++= {
        if (isScala3(scalaVersion.value))
          Seq("-noindent")
        else
          Seq()
      },
      scalacOptions --= {
        if (isScala3(scalaVersion.value))
          Seq("-Xfatal-warnings")
        else
          Seq()
      },
      Test / parallelExecution := {
        val old = (Test / parallelExecution).value
        if (isScala3(scalaVersion.value)) {
          false
        } else {
          old
        }
      }
    )
  }
}
