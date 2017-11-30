package bitpeace

import cats.effect.IO
import doobie._, doobie.implicits._

object H2 {

  def tx(db: String): Transactor[IO] =
    Transactor.fromDriverManager[IO](
      "org.h2.Driver", s"jdbc:h2:$db", "sa", ""
    )

  def dropDatabase =
    sql"drop all objects delete files;".update
}
