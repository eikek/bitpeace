package bitpeace

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._

trait BitpeaceTestSuite extends TransactorTestSuite {

  val config = BitpeaceConfig.defaultTika[IO]

  override def setup(): DbSetup = {
    val dbs = super.setup()
    BitpeaceTables(config).create(dbs.dbms).transact(dbs.xa).unsafeRunSync()
    dbs
  }

}
