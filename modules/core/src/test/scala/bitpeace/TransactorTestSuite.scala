package bitpeace

import cats.effect._
import cats.effect.unsafe.implicits.global
import minitest._

trait TransactorTestSuite extends TestSuite[DbSetup] with Helpers {

  def dbSetup: DB[IO] = DB.H2

  def setup(): DbSetup = {
    val DB     = dbSetup
    val dbname = s"testdb${TransactorTestSuite.counter.getAndIncrement}"
    DbSetup(dbname, DB.dbms, DB.tx(dbname))
  }

  def tearDown(p: DbSetup): Unit = {
    val DB = dbSetup
    DB.dropDatabase(p.dbname, p.xa).unsafeRunSync()
  }

}

object TransactorTestSuite {
  private val counter = new java.util.concurrent.atomic.AtomicLong(0)

}
