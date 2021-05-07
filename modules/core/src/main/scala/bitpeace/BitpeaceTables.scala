package bitpeace

import bitpeace.sql.Dbms
import cats.free.Free
import doobie.free.connection.ConnectionOp

case class BitpeaceTables[F[_]](cfg: BitpeaceConfig[F]) {
  private val stmt = sql.Statements(cfg)

  def create(db: Dbms): Free[ConnectionOp, Unit] =
    for {
      _ <- stmt.createMetaTable.run
      _ <- stmt.createChunkTable(db).run
    } yield ()
}
