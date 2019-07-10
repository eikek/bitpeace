package bitpeace

import cats.effect._
import doobie._
import bitpeace.sql.Dbms

case class DbSetup(dbname: String, dbms: Dbms, xa: Transactor[IO])
