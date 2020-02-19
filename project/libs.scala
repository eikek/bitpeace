import sbt._

object libs {

  val `scala-version` = "2.13.1"

  // https://github.com/typelevel/cats
  // MIT http://opensource.org/licenses/mit-license.php
  val `cats-core` = "org.typelevel" %% "cats-core" % "2.0.0"

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val `fs2-core` = "co.fs2" %% "fs2-core" % "2.1.0"
  val `fs2-io` = "co.fs2" %% "fs2-io" % "2.2.2"

  // https://github.com/tpolecat/doobie
  // MIT
  val `doobie-core` = "org.tpolecat" %% "doobie-core" % "0.8.8"
  val `doobie-hikari` = "org.tpolecat" %% "doobie-hikari" % "0.8.8"

  // https://github.com/scodec/scodec-bits
  // 3-clause BSD
  val `scodec-bits` = "org.scodec" %% "scodec-bits" % "1.1.13"

  // https://jdbc.postgresql.org/
  // BSD
  val postgres = "org.postgresql" % "postgresql" % "42.2.9"

  // https://github.com/MariaDB/mariadb-connector-j
  // LGPL-2.1
  val mariadb = "org.mariadb.jdbc" % "mariadb-java-client" % "2.5.4"

  // https://github.com/h2database/h2database
  // MPL 2.0 or EPL 1.0
  val h2 = "com.h2database" % "h2" % "1.4.200"

  // http://tika.apache.org
  // ASL 2.0
  val tika = "org.apache.tika" % "tika-core" % "1.23"

  // https://github.com/Log4s/log4s
  // ASL 2.0
  val log4s = "org.log4s" %% "log4s" % "1.4.0"

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3"

  // https://github.com/monix/minitest
  // Apache 2.0
  val minitest = "io.monix" %% "minitest" % "2.7.0"
}
