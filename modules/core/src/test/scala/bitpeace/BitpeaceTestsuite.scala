package bitpeace

import cats.effect.IO
import doobie._, doobie.implicits._

trait BitpeaceTestSuite extends TransactorTestSuite {

  val config = BitpeaceConfig.defaultTika[IO]

  override def setup(): Transactor[IO] = {
    val xa = super.setup()
    BitpeaceTables(config).create(sql.Dbms.H2).transact(xa).unsafeRunSync
    xa
  }

}
