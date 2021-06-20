package bitpeace

import cats.effect._
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import munit._

trait Fixtures { self: FunSuite =>

  def dbFixture(db: DB[IO], createTables: Boolean) = FunFixture[DbSetup](
    setup = { _ =>
      val res = setup(db)
      if (createTables) {
        BitpeaceTables(Fixtures.config)
          .create(res.dbms.dbms)
          .transact(res.xa)
          .unsafeRunSync()
      }
      res
    },
    teardown = { setup =>
      tearDown(setup)
    }
  )

  private def setup(db: DB[IO]): DbSetup = {
    val dbname = s"testdb${Fixtures.counter.getAndIncrement}"
    DbSetup(dbname, db, db.tx(dbname))
  }

  private def tearDown(p: DbSetup): Unit =
    p.dbms.dropDatabase(p.dbname, p.xa).unsafeRunSync()

}
object Fixtures {
  val config = BitpeaceConfig.defaultTika[IO]

  private val counter = new java.util.concurrent.atomic.AtomicLong(0)
}
