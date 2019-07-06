package bitpeace

import cats.effect.IO
import doobie.implicits._

object BitpeaceTablesSpec extends TransactorTestSuite {

  val config = BitpeaceConfig.default[IO]
  val tables = BitpeaceTables(config)

  test ("create tables") { p =>
    tables.create(sql.Dbms.H2).transact(p._2).unsafeRunSync
    val c = sql.Statements(config).count.transact(p._2).unsafeRunSync
    assertEquals(c, 0)
  }
}
