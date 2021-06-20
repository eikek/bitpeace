package bitpeace

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._

object BitpeaceTablesSpec extends TransactorTestSuite {

  val config = BitpeaceConfig.default[IO]
  val tables = BitpeaceTables(config)

//  override val dbSetup = DB.Postgres

  test("create tables") { p =>
    tables.create(p.dbms).transact(p.xa).unsafeRunSync()
    val c = sql.Statements(config).count.transact(p.xa).unsafeRunSync()
    assertEquals(c, 0)
  }
}
