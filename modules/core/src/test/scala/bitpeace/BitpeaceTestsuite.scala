package bitpeace

import java.nio.file.Path
import cats.effect.IO
import doobie._, doobie.implicits._

trait BitpeaceTestSuite extends TransactorTestSuite {

  val config = BitpeaceConfig.defaultTika[IO]

  override def setup(): (Path, Transactor[IO]) = {
    val (file, xa) = super.setup()
    BitpeaceTables(config).create(sql.Dbms.H2).transact(xa).unsafeRunSync
    (file, xa)
  }

}
