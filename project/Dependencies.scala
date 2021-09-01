import sbt._

object Dependencies {

  object Version {
    val activation      = "2.0.1"
    val doobie          = "1.0.0-RC1"
    val fs2             = "3.0.4"
    val h2              = "1.4.200"
    val log4s           = "1.4.0"
    val logback         = "1.2.5"
    val mariadb         = "2.7.4"
    val munitVersion    = "0.7.29"
    val organizeImports = "0.5.0"
    val postgres        = "42.2.23"
    val scala212        = "2.12.14"
    val scala213        = "2.13.6"
    val scala3          = "3.0.2"
    val scodec          = "1.1.28"
    val tika            = "2.1.0"
  }

  val munit = Seq(
    "org.scalameta" %% "munit"            % Version.munitVersion,
    "org.scalameta" %% "munit-scalacheck" % Version.munitVersion
  )

  val activation = Seq(
    "jakarta.activation" % "jakarta.activation-api" % Version.activation
  )

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val fs2Io = Seq(
    "co.fs2" %% "fs2-io" % Version.fs2
  )

  // https://github.com/tpolecat/doobie
  // MIT
  val doobieCore = Seq(
    "org.tpolecat" %% "doobie-core" % Version.doobie
  )

  // https://github.com/scodec/scodec-bits
  // 3-clause BSD
  val scodecBits = Seq(
    "org.scodec" %% "scodec-bits" % Version.scodec
  )

  // https://jdbc.postgresql.org/
  // BSD
  val postgres = Seq(
    "org.postgresql" % "postgresql" % Version.postgres
  )

  // https://github.com/MariaDB/mariadb-connector-j
  // LGPL-2.1
  val mariadb = Seq(
    "org.mariadb.jdbc" % "mariadb-java-client" % Version.mariadb
  )

  // https://github.com/h2database/h2database
  // MPL 2.0 or EPL 1.0
  val h2 = Seq(
    "com.h2database" % "h2" % Version.h2
  )

  // http://tika.apache.org
  // ASL 2.0
  val tika = Seq(
    "org.apache.tika" % "tika-core" % Version.tika
  )

  // https://github.com/Log4s/log4s
  // ASL 2.0
  val log4s = Seq(
    "org.log4s" %% "log4s" % Version.log4s
  )

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val logbackClassic = Seq(
    "ch.qos.logback" % "logback-classic" % Version.logback
  )

}
