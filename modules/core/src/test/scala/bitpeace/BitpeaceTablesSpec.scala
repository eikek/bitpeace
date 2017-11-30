package bitpeace

import fs2.interop.cats._
import doobie.imports._
import fs2.Task

object BitpeaceTablesSpec extends TransactorTestSuite {

  val config = BitpeaceConfig.default[Task]
  val tables = BitpeaceTables(config)

  test ("create tables") { xa =>
    tables.create(sql.Dbms.H2).transact(xa).unsafeRun
    val c = sql.Statements(config).count.transact(xa).unsafeRun
    assertEquals(c, 0)
  }
}
