import libs._

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
  crossScalaVersions := Seq("2.12.8", `scala-version`),
  scalaVersion := `scala-version`,
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
)

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
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseCrossBuild := true
)

lazy val coreDeps = Seq(`doobie-core`, `scodec-bits`, tika % "provided")
lazy val testDeps = Seq(minitest, h2, postgres, mariadb, `fs2-io`).map(_ % "test")

lazy val core = project.in(file("modules/core")).
  settings(sharedSettings).
  settings(publishSettings).
  settings(Seq(
    name := "bitpeace-core",
    description := "Library for dealing with binary data using doobie.",
    libraryDependencies ++= coreDeps ++ testDeps
  ))

lazy val root = project.in(file(".")).
  disablePlugins(ReleasePlugin).
  settings(sharedSettings).
  settings(
    skip in publish := true
  ).
  aggregate(core)
