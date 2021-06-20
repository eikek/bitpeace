import Dependencies._
import com.typesafe.sbt.SbtGit.GitKeys._
import sbt.nio.file.FileTreeView

addCommandAlias("ci", "; lint; +test; readme/updateReadme; +publishLocal")
addCommandAlias(
  "lint",
  "scalafmtSbtCheck; scalafmtCheckAll; Compile/scalafix --check; Test/scalafix --check"
)
addCommandAlias("fix", "; Compile/scalafix; Test/scalafix; scalafmtSbt; scalafmtAll")

lazy val sharedSettings = Seq(
  name := "bitpeace",
  organization := "com.github.eikek",
  licenses := Seq("MIT" -> url("http://spdx.org/licenses/MIT")),
  homepage := Some(url("https://github.com/eikek/bitpeace")),
  crossScalaVersions := Seq(Version.scala212, Version.scala213, Version.scala3),
  scalaVersion := Version.scala213,
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
       else if (scalaBinaryVersion.value == "3")
         List(
           "-explain",
           "-explain-types",
           "-indent",
           "-print-lines",
           "-Ykind-projector",
           "-Xmigration",
           "-Xfatal-warnings"
         )
       else
         Nil),
  Compile / console / scalacOptions ~= (_.filterNot(
    Set("-Xfatal-warnings", "-Ywarn-unused-import").contains
  )),
  Test / scalacOptions := (Compile / console / scalacOptions).value,
  testFrameworks += new TestFramework("minitest.runner.Framework"),
  versionScheme := Some("early-semver")
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
  Test / publishArtifact := false
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

val scalafixSettings = Seq(
  semanticdbEnabled := true,                        // enable SemanticDB
  semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
  ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % Version.organizeImports
)

lazy val coreDeps =
  Seq(doobieCore, scodecBits, activation, tika.intransitive % "provided")
lazy val testDeps = Seq(minitest, h2, postgres, mariadb, fs2Io).map(_ % "test")

lazy val core = project
  .in(file("modules/core"))
  .settings(sharedSettings)
  .settings(publishSettings)
  .settings(scalafixSettings)
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
      "VERSION" -> latestRelease.value
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
  project
    .in(file("."))
    .settings(sharedSettings)
    .settings(noPublish)
    .settings(
      crossScalaVersions := Nil
    )
    .aggregate(core)
