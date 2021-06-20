package bitpeace

import cats.effect.unsafe.implicits.global
import doobie.implicits._
import munit._

class BitpeaceTablesSpec extends FunSuite with Fixtures with Helpers {

  val tables = BitpeaceTables(Fixtures.config)

  val db = dbFixture(DB.H2, createTables = false)

  db.test("create tables") { p =>
    tables.create(p.dbms.dbms).transact(p.xa).unsafeRunSync()
    val c = sql.Statements(Fixtures.config).count.transact(p.xa).unsafeRunSync()
    assertEquals(c, 0L)
  }
}
