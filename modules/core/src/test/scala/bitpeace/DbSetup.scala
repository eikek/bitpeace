package bitpeace

import cats.effect._
import doobie._

case class DbSetup(dbname: String, dbms: DB[IO], xa: Transactor[IO])
