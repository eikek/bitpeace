import libs._
import com.typesafe.sbt.SbtGit.GitKeys._
import xerial.sbt.Sonatype._
import ReleaseTransformations._
import sbt.nio.file.FileTreeView

lazy val sharedSettings = Seq(
  name := "bitpeace",
  organization := "com.github.eikek",
  licenses := Seq("MIT" -> url("http://spdx.org/licenses/MIT")),
  homepage := Some(url("https://github.com/eikek/bitpeace")),
  crossScalaVersions := Seq(scala212, scala213),
  scalaVersion := scala213,
  scalacOptions ++=
    Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:higherKinds"
    ) ++
      (if (scalaBinaryVersion.value.startsWith("2.12"))
         List(
           "-Xfatal-warnings", // fail when there are warnings
           "-Xlint",
           "-Yno-adapted-args",
           "-Ywarn-dead-code",
           "-Ywarn-unused-import",
           "-Ypartial-unification",
           "-Ywarn-value-discard"
         )
       else if (scalaBinaryVersion.value.startsWith("2.13"))
         List("-Werror", "-Wdead-code", "-Wunused", "-Wvalue-discard")
       else
         Nil),
  scalacOptions in (Compile, console) ~= (_.filterNot(
    Set("-Xfatal-warnings", "-Ywarn-unused-import").contains
  )),
  scalacOptions in Test := (scalacOptions in (Compile, console)).value,
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
  sonatypeProjectHosting := Some(
    GitHubHosting("eikek", "bitpeace", "eike.kettner@posteo.de")
  )
)

val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    gitHeadCommit,
    gitHeadCommitDate,
    gitUncommittedChanges,
    gitDescribedVersion
  ),
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoOptions += BuildInfoOption.BuildTime
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val coreDeps = Seq(doobieCore, scodecBits, tika % "provided")
lazy val testDeps = Seq(minitest, h2, postgres, mariadb, fs2Io).map(_ % "test")

lazy val core = project
  .in(file("modules/core"))
  .settings(sharedSettings)
  .settings(publishSettings)
  .settings(
    Seq(
      name := "bitpeace-core",
      description := "Library for dealing with binary data using doobie.",
      libraryDependencies ++= coreDeps ++ testDeps
    )
  )

val updateReadme = inputKey[Unit]("Update readme")
lazy val readme = project
  .in(file("modules/readme"))
  .enablePlugins(MdocPlugin)
  .settings(sharedSettings)
  .settings(noPublish)
  .settings(
    name := "bitpeace-readme",
    scalacOptions := Seq(),
    libraryDependencies ++= Seq(tika, h2),
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    updateReadme := {
      FileTreeView.default
        .list(file("/tmp").toGlob / "bitpeace-testdb*")
        .map(_._1.toFile)
        .foreach(IO.delete)
      val _      = mdoc.evaluated
      val out    = mdocOut.value / "readme.md"
      val target = (LocalRootProject / baseDirectory).value / "README.md"
      val logger = streams.value.log
      logger.info(s"Updating readme: $out -> $target")
      IO.copyFile(out, target)
      ()
    }
  )
  .dependsOn(core)

lazy val root =
  project.in(file(".")).settings(sharedSettings).settings(noPublish).aggregate(core)
