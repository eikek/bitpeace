package bitpeace

import cats.free.Free
import doobie.free.connection.ConnectionOp
import bitpeace.sql.Dbms

case class BitpeaceTables[F[_]](cfg: BitpeaceConfig[F]) {
  private val stmt = sql.Statements(cfg)

  def create(db: Dbms): Free[ConnectionOp, Unit] =
    for {
      a <- stmt.createMetaTable(db).run
      b <- stmt.createChunkTable(db).run
    } yield ()
}
