package bitpeace

import fs2.Task
import fs2.interop.cats._
import doobie.imports._

object H2 {

  def tx(db: String): Transactor[Task] =
    Transactor.fromDriverManager[Task](
      "org.h2.Driver", s"jdbc:h2:$db", "sa", ""
    )

  def dropDatabase =
    sql"drop all objects delete files;".update
}
