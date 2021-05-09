package bitpeace

import bitpeace.sql.Dbms
import cats.effect._
import doobie._

case class DbSetup(dbname: String, dbms: Dbms, xa: Transactor[IO])
