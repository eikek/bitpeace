import libs._
import xerial.sbt.Sonatype._
import ReleaseTransformations._

val scalacOpts: Seq[String] = Seq(
  "-encoding", "UTF-8",
  "-Xfatal-warnings",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:higherKinds",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import"
)

lazy val sharedSettings = Seq(
  name := "bitpeace",
  organization := "com.github.eikek",
  licenses := Seq("MIT" -> url("http://spdx.org/licenses/MIT")),
  homepage := Some(url("https://github.com/eikek/bitpeace")),
  crossScalaVersions := Seq(scala212, scala213),
  scalaVersion := scala213,
  scalacOptions := {
    if (scalaBinaryVersion.value.startsWith("2.13")) {
      scalacOpts.filter(o => o != "-Yno-adapted-args" && o != "-Ywarn-unused-import")
    } else {
      scalacOpts
    }
  },
  scalacOptions in (Compile, console) ~= (_ filterNot (Set("-Xfatal-warnings", "-Ywarn-unused-import").contains)),
  scalacOptions in (Test) := (scalacOptions in (Compile, console)).value,
  testFrameworks += new TestFramework("minitest.runner.Framework")
) ++ publishSettings

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/eikek/bitpeace.git"),
      "scm:git:git@github.com:eikek/bitpeace.git"
    )
  ),
  developers := List(
    Developer(
      id = "eikek",
      name = "Eike Kettner",
      url = url("https://github.com/eikek"),
      email = ""
    )
  ),
  publishTo := sonatypePublishToBundle.value,
  publishArtifact in Test := false,
  releaseCrossBuild := true,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    // For non cross-build projects, use releaseStepCommand("publishSigned")
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
  sonatypeProjectHosting := Some(GitHubHosting("eikek", "yamusca", "eike.kettner@posteo.de"))
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val coreDeps = Seq(doobieCore, scodecBits, tika % "provided")
lazy val testDeps = Seq(minitest, h2, postgres, mariadb, fs2Io).map(_ % "test")

lazy val core = project.in(file("modules/core")).
  settings(sharedSettings).
  settings(publishSettings).
  settings(Seq(
    name := "bitpeace-core",
    description := "Library for dealing with binary data using doobie.",
    libraryDependencies ++= coreDeps ++ testDeps
  ))

lazy val root = project.in(file(".")).
  settings(sharedSettings).
  settings(noPublish).
  aggregate(core)
