import sbt._

object libs {

  val scala213 = "2.13.1"
  val scala212 = "2.12.11"

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val fs2Io = "co.fs2" %% "fs2-io" % "2.3.0"

  // https://github.com/tpolecat/doobie
  // MIT
  val doobieCore = "org.tpolecat" %% "doobie-core" % "0.9.0"

  // https://github.com/scodec/scodec-bits
  // 3-clause BSD
  val scodecBits = "org.scodec" %% "scodec-bits" % "1.1.14"

  // https://jdbc.postgresql.org/
  // BSD
  val postgres = "org.postgresql" % "postgresql" % "42.2.12"

  // https://github.com/MariaDB/mariadb-connector-j
  // LGPL-2.1
  val mariadb = "org.mariadb.jdbc" % "mariadb-java-client" % "2.6.0"

  // https://github.com/h2database/h2database
  // MPL 2.0 or EPL 1.0
  val h2 = "com.h2database" % "h2" % "1.4.200"

  // http://tika.apache.org
  // ASL 2.0
  val tika = "org.apache.tika" % "tika-core" % "1.24"

  // https://github.com/Log4s/log4s
  // ASL 2.0
  val log4s = "org.log4s" %% "log4s" % "1.4.0"

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3"

  // https://github.com/monix/minitest
  // Apache 2.0
  val minitest = "io.monix" %% "minitest" % "2.8.2"
}
