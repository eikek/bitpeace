package bitpeace

import fs2.Task
import fs2.interop.cats._
import doobie.imports._

trait BitpeaceTestSuite extends TransactorTestSuite {

  val config = BitpeaceConfig.defaultTika[Task]

  override def setup(): Transactor[Task] = {
    val xa = super.setup()
    BitpeaceTables(config).create(sql.Dbms.H2).transact(xa).unsafeRun
    xa
  }

}
