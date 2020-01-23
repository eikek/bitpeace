package bitpeace.sql

import java.time.Instant

import doobie._, doobie.implicits._
import scodec.bits.ByteVector
import cats.implicits._

import bitpeace._

trait Statements[F[_]] {
  val config: BitpeaceConfig[F]

  def insertChunk(ch: FileChunk): Update0 =
    (fr"""INSERT INTO """ ++ chunkTable ++
      fr"""(fileId,chunkNr,chunkLength,chunkData) VALUES """ ++
      sql"""(${ch.fileId}, ${ch.chunkNr}, ${ch.chunkLength}, ${ch.chunkData})""")
      .update

  def tryUpdateFileId(old: String, id: String) = {
    for {
      m <- (fr"""UPDATE """ ++ chunkTable ++ sql""" SET fileId = $id WHERE fileId = $old""").update.run.attemptSql
      n <- (fr"UPDATE " ++ metaTable ++ sql" SET id = $id WHERE id = $old").update.run.attemptSql
    } yield n |+| m
  }

  private def chunkCondition(id: String, offset: Option[Int], limit: Option[Int]) = {
    val min = offset.map(n => fr" AND chunkNr >= $n").getOrElse(fr"")
    val max = limit.map(n => fr" AND chunkNr < ${offset.map(_ + n).getOrElse(n)}").getOrElse(fr"")
    sql" WHERE fileId = $id" ++ min ++ max ++ fr" ORDER BY chunkNr ASC"
  }

  def selectChunkData(id: String, offset: Option[Int] = None, limit: Option[Int] = None): Query0[ByteVector] = {
    (fr"""SELECT chunkData FROM """ ++ chunkTable ++ chunkCondition(id, offset, limit))
      .query[ByteVector]
  }

  def selectChunks(id: String, offset: Option[Int] = None, limit: Option[Int] = None): Query0[FileChunk] = {
    (fr"""SELECT fileId, chunkNr, chunkData FROM """ ++ chunkTable ++ chunkCondition(id, offset, limit))
      .query[FileChunk]
  }

  def selectChunkArray(id: String, offset: Option[Int] = None, limit: Option[Int] = None): Query0[Array[Byte]] = {
    val q = fr"SELECT chunkData FROM" ++ chunkTable ++ chunkCondition(id, offset, limit)
    q.query[Array[Byte]]
  }

  def deleteChunks(id: String): Update0 =
    (fr"""DELETE FROM """ ++ chunkTable ++ sql""" WHERE fileId = $id""").update

  def deleteFileMeta(id: String): Update0 =
    (fr"""DELETE FROM """ ++ metaTable ++ sql""" WHERE id = $id""").update

  def insertFileMeta(fm: FileMeta): Update0 =
    (fr"""INSERT INTO """ ++ metaTable ++ sql"""(id,timestamp,mimetype,length,checksum,chunks,chunksize) VALUES (
      ${fm.id}, ${fm.timestamp}, ${fm.mimetype}, ${fm.length}, ${fm.checksum}, ${fm.chunks}, ${fm.chunksize}
    )
    """).update

  def updateFileMeta(id: String, timestamp: Instant, checksum: String): Update0 = {
    val len = fr"(SELECT SUM(LENGTH(chunkData)) FROM" ++ chunkTable ++ fr"WHERE fileId = $id)"
      (fr"UPDATE " ++ metaTable ++
        sql" SET timestamp = ${timestamp}, checksum = ${checksum}, length = " ++
        len ++
        fr"WHERE id = $id").update
  }

  def updateMimetype(id: String, mimetype: Mimetype): Update0 =
    (fr"UPDATE " ++ metaTable ++ sql" SET mimetype = $mimetype WHERE id = $id").update

  def fileExists(id: String): ConnectionIO[Option[String]] =
    (fr"""SELECT id FROM """ ++ metaTable ++ sql""" WHERE id = $id""").query[String].option

  def chunkExists(id: String, chunkNr: Long): ConnectionIO[Boolean] = {
    (fr"SELECT count(*) FROM " ++ chunkTable ++ sql"as fc WHERE fc.fileId = $id AND fc.chunknr = $chunkNr").
      query[Int].
      unique.
      map(_ > 0)
  }

  def chunkExistsRemove(id: String, chunkNr: Long, size: Long) = {
    for {
      b <- chunkExists(id, chunkNr)
      f <- if (b) chunkLengthCheckOrRemove(id, chunkNr, size) else b.pure[ConnectionIO]
    } yield f
  }

  def chunkLengthCheckOrRemove(fileId: String, chunkNr: Long, chunkLength: Long) = {
    val query = fr"SELECT count(*) FROM" ++ chunkTable ++
      fr"WHERE fileId = $fileId AND chunknr = $chunkNr AND length(chunkData) != ${chunkLength}"

    val delete = (fr"DELETE FROM" ++ chunkTable ++ fr"WHERE fileId = $fileId AND chunknr = $chunkNr").update.run

    for {
      n <- query.query[Int].unique
      _ <- if (n == 1) delete else 0.pure[ConnectionIO]
    } yield n == 0
  }


  def selectFileMeta(id: String): ConnectionIO[Option[FileMeta]] =
    (fr"""SELECT id, timestamp, mimetype, length, checksum, chunks, chunksize FROM """ ++
      metaTable ++
      sql""" WHERE id = $id""")
      .query[FileMeta]
      .option


  def count: ConnectionIO[Long] =
    (fr"SELECT count(*) from" ++ metaTable).query[Long].unique

  def countChunks(id: String): ConnectionIO[Long] =
    (fr"SELECT count(*) FROM " ++ chunkTable ++ sql" WHERE fileId = $id").query[Long].unique

  def createMetaTable(db: Dbms): Update0 = (fr"""
    CREATE TABLE """ ++ metaTable ++ fr""" (
      id varchar(254) not null,
      timestamp varchar(40) not null,
      mimetype varchar(254) not null,
      length bigint not null,
      checksum varchar(254) not null,
      chunks int not null,
      chunksize int not null,
      primary key (id)
    );
  """).update

  def createChunkTable(db: Dbms): Update0 = (fr"""
    CREATE TABLE """ ++ chunkTable ++ fr""" (
      fileId varchar(254) not null,
      chunkNr int not null,
      chunkLength int not null,
      chunkData""" ++ db.blobType ++ fr""" not null,
      primary key (fileId, chunkNr)
    );""").update

  private def chunkTable = Fragment.const(config.chunkTable)
  private def metaTable = Fragment.const(config.metaTable)
}

object Statements {

  def apply[F[_]](cfg: BitpeaceConfig[F]): Statements[F] =
    new Statements[F] {
      val config = cfg
    }
}
