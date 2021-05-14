import sbt._

object Dependencies {

  object Version {
    val activation      = "2.0.1"
    val doobie          = "0.13.2"
    val fs2             = "2.5.6"
    val h2              = "1.4.200"
    val log4s           = "1.4.0"
    val logback         = "1.2.3"
    val mariadb         = "2.7.3"
    val minitest        = "2.9.6"
    val organizeImports = "0.5.0"
    val postgres        = "42.2.20"
    val scala212        = "2.12.13"
    val scala213        = "2.13.5"
    val scodec          = "1.1.27"
    val tika            = "1.26"
  }

  val activation = "jakarta.activation" % "jakarta.activation-api" % Version.activation

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val fs2Io = "co.fs2" %% "fs2-io" % Version.fs2

  // https://github.com/tpolecat/doobie
  // MIT
  val doobieCore = "org.tpolecat" %% "doobie-core" % Version.doobie

  // https://github.com/scodec/scodec-bits
  // 3-clause BSD
  val scodecBits = "org.scodec" %% "scodec-bits" % Version.scodec

  // https://jdbc.postgresql.org/
  // BSD
  val postgres = "org.postgresql" % "postgresql" % Version.postgres

  // https://github.com/MariaDB/mariadb-connector-j
  // LGPL-2.1
  val mariadb = "org.mariadb.jdbc" % "mariadb-java-client" % Version.mariadb

  // https://github.com/h2database/h2database
  // MPL 2.0 or EPL 1.0
  val h2 = "com.h2database" % "h2" % Version.h2

  // http://tika.apache.org
  // ASL 2.0
  val tika = "org.apache.tika" % "tika-core" % Version.tika

  // https://github.com/Log4s/log4s
  // ASL 2.0
  val log4s = "org.log4s" %% "log4s" % Version.log4s

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % Version.logback

  // https://github.com/monix/minitest
  // Apache 2.0
  val minitest = "io.monix" %% "minitest" % Version.minitest
}
