package bitpeace

import cats.effect.IO
import doobie.implicits._

object BitpeaceTablesSpec extends TransactorTestSuite {

  val config = BitpeaceConfig.default[IO]
  val tables = BitpeaceTables(config)

  test ("create tables") { xa =>
    tables.create(sql.Dbms.H2).transact(xa).unsafeRunSync
    val c = sql.Statements(config).count.transact(xa).unsafeRunSync
    assertEquals(c, 0)
  }
}
