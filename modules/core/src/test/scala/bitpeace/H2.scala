package bitpeace

import cats.effect._
import doobie._, doobie.implicits._

object H2 {

  def tx(db: String)(implicit C: ContextShift[IO]): Transactor[IO] =
    Transactor.fromDriverManager[IO](
      "org.h2.Driver", s"jdbc:h2:$db", "sa", ""
    )

  def dropDatabase =
    sql"drop all objects delete files;".update
}
