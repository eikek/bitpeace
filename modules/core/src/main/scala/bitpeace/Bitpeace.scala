package bitpeace

import java.time.Instant
import cats.effect.{Effect, Sync}
import cats.data._
import cats.implicits._
import fs2.{Chunk, Pipe, RaiseThrowable, Stream}
import fs2.Chunk.ByteVectorChunk
import doobie._, doobie.implicits._
import scodec.bits.ByteVector

/** A store for binary data.
  *
  * Binary data is given as a stream of bytes. The stream is saved in
  * chunks, where each chunk is stored into a blob object (in contrast
  * to use one blob for the whole data). This makes it possible to
  * effectively retrieve partial content.
  */
trait Bitpeace[F[_]] {

  /** Save data in chunks of size `chunkSize` and use a random id.
    */
  def saveNew(
      data: Stream[F, Byte],
      chunkSize: Int,
      hint: MimetypeHint,
      fileId: Option[String] = None,
      time: Instant = Instant.now
  ): Stream[F, FileMeta]

  /** Save data in chunks of size `chunkSize` and check for duplicates.
    *
    * Return either {{{Outcome.Created}}} if new data has been written
    * (no duplicates) or {{{Outcome.Unmodified}}} if no data was
    * written and the duplicate is returned.
    */
  def save(
      data: Stream[F, Byte],
      chunkSize: Int,
      hint: MimetypeHint,
      time: Instant = Instant.now
  ): Stream[F, Outcome[FileMeta]] =
    saveNew(data, chunkSize, hint, None, time).flatMap(makeUnique)

  /** “Merge” duplicates.
    *
    * If the given {{{meta}}} object contains a random id (as returned
    * from {{{saveNew}}}), check for duplicates using its checksum.
    *
    * If a duplicate is found, delete {{{meta}}} and its data and
    * return the just found value. If no duplicate is found, update
    * the id of {{{meta}}} to be its checksum.
    *
    * Return {{{Outcome.Unmodified}}} if there was a duplicate,
    * or {{{Outcome.Created}}} if there was no duplicate.
    */
  def makeUnique(meta: FileMeta): Stream[F, Outcome[FileMeta]]

  /** Adds a new chunk of data to a file.
    *
    * Creates or updates the corresponding [[FileMeta]] record and
    * returns it. This is useful if you get chunks of data in some
    * random order.
    *
    * If the chunk already exists with correct length, the return
    * value is wrapped in [[Outcome.Unmodified]] and the given chunk
    * is not written, otherwise a [[Outcome#Created]] is returned and
    * the chunk is stored.
    */
  def addChunk(
      chunk: FileChunk,
      chunksize: Int,
      totalChunks: Int,
      hint: MimetypeHint
  ): Stream[F, Outcome[FileMeta]]

  /** Calculates the total number of chunks from the given length and calls [[addChunk]]. */
  def addChunkByLength(
      chunk: FileChunk,
      chunksize: Int,
      length: Long,
      hint: MimetypeHint
  ): Stream[F, Outcome[FileMeta]] = {
    val nChunks = length / chunksize + (if (length % chunksize == 0) 0 else 1)
    addChunk(chunk, chunksize, nChunks.toInt, hint)
  }

  /** Return meta data about one file. */
  def get(id: String): Stream[F, Option[FileMeta]]

  /** Fetch data using one connection per chunk. So connections are
    * closed immediately after reading a chunk. */
  def fetchData(range: RangeDef): Pipe[F, FileMeta, Byte]

  /** Fetch data using one connection for the whole stream. It is closed
    * once the stream terminates. */
  def fetchData2(range: RangeDef): Pipe[F, FileMeta, Byte]

  /** Return whether a file with a given id exists. */
  def exists(id: String): Stream[F, Boolean]

  /** Return whether a file with given id has a chunk with the given
    * chunkNr. */
  def chunkExists(id: String, chunkNr: Long): Stream[F, Boolean]

  /** Like {{{chunkExists}} but also checks the chunk size. If a chunk
    * with different size exists, it is removed and {{{false}}} is
    * returned. */
  def chunkExistsRemove(id: String, chunkNr: Long, chunkLength: Long): Stream[F, Boolean]

  /** Deletes the file data and meta-data. */
  def delete(id: String): Stream[F, Boolean]

  /** Count all FileMeta rows. */
  def count: Stream[F, Long]

  // low level
  /** Insert the file meta object. */
  def saveFileMeta(fm: FileMeta): Stream[F, Unit]

  /** Insert the chunk object. */
  def saveFileChunk(fc: FileChunk): Stream[F, Unit]

  /** Get chunks using one connection. */
  def getChunks(
      id: String,
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): Stream[F, FileChunk]
}

object Bitpeace {

  def apply[F[_]](
      config: BitpeaceConfig[F],
      xa: Transactor[F]
  )(implicit F: Effect[F], ev: RaiseThrowable[F]): Bitpeace[F] = new Bitpeace[F] {
    val stmt = sql.Statements(config)

    def saveNew(
        data: Stream[F, Byte],
        chunkSize: Int,
        hint: MimetypeHint,
        fileId: Option[String],
        time: Instant
    ): Stream[F, FileMeta] =
      Stream.eval(fileId.map(F.pure).getOrElse(config.randomIdGen)).flatMap { id =>
        data
          .through(rechunk(chunkSize))
          .zipWithIndex
          .map(t => FileChunk(id, t._2, t._1))
          .flatMap(ch =>
            Stream.eval(F.map(stmt.insertChunk(ch).run.transact(xa))(_ => ch))
          )
          .through(accumulateKey(time, chunkSize, hint))
          .map(key => key.copy(id = id))
          .flatMap(key =>
            Stream.eval(stmt.insertFileMeta(key).run.transact(xa)).map(_ => key)
          )
      }

    def makeUnique(k: FileMeta): Stream[F, Outcome[FileMeta]] =
      if (k.id == k.checksum) Stream.emit(Outcome.Unmodified(k))
      else
        get(k.checksum).flatMap {
          case Some(fm) =>
            delete(k.id).map(_ => Outcome.Unmodified(fm))
          case None =>
            val io = Sync[ConnectionIO]
            val update =
              stmt.tryUpdateFileId(k.id, k.checksum).transact(xa).flatMap {
                case Right(_) =>
                  Sync[F]
                    .pure[Outcome[FileMeta]](Outcome.Created(k.copy(id = k.checksum)))
                case Left(sqlex) =>
                  //fix unique constraint error, fail on everything else
                  val rem = for {
                    m <- stmt.selectFileMeta(k.checksum).flatMap {
                      case Some(meta) =>
                        io.pure[Outcome[FileMeta]](Outcome.Unmodified(meta))
                      case None =>
                        io.raiseError[Outcome[FileMeta]] {
                          new Exception(
                            s"Cannot update file key $k but cannot find it either",
                            sqlex
                          )
                        }
                    }
                    _ <- stmt.deleteFileMeta(k.id).run
                    _ <- stmt.deleteChunks(k.id).run
                  } yield m
                  rem.transact(xa)
              }
            Stream.eval(update)
        }

    def addChunk(
        chunk: FileChunk,
        chunksize: Int,
        nChunks: Int,
        hint: MimetypeHint
    ): Stream[F, Outcome[FileMeta]] =
      chunkExistsRemove(chunk.fileId, chunk.chunkNr, chunk.chunkLength).flatMap {
        case true =>
          get(chunk.fileId).flatMap {
            case Some(m) => Stream.emit(Outcome.Unmodified(m))
            case None =>
              Stream.raiseError(
                new Exception(s"Chunk $chunk exists, but not corresponding FileMeta")
              )
          }

        case false =>
          val updateMime: Stream[F, FileMeta] =
            if (chunk.chunkNr != 0) Stream.empty
            else
              Stream
                .eval((for {
                  _ <- stmt
                    .updateMimetype(
                      chunk.fileId,
                      config.mimetypeDetect.fromBytes(chunk.chunkData, hint)
                    )
                    .run
                  m <- stmt.selectFileMeta(chunk.fileId)
                } yield m).transact(xa))
                .unNoneTerminate

          val updateMeta: Stream[F, FileMeta] = {
            val makeSha = makeChecksum(chunk.fileId, nChunks)
            makeSha.flatMap { checksum =>
              Stream
                .eval((for {
                  _ <- stmt.updateFileMeta(chunk.fileId, Instant.now, checksum).run
                  m <- stmt.selectFileMeta(chunk.fileId)
                } yield m).transact(xa))
                .unNoneTerminate
            }
          }

          def tryInsertMeta(fm: FileMeta) =
            stmt.insertFileMeta(fm).run.attemptSql.transact(xa).flatMap {
              case Right(_) =>
                Effect[F].pure(fm)
              case Left(sqlex) =>
                stmt.selectFileMeta(fm.id).transact(xa).flatMap {
                  case Some(m) => Effect[F].pure(m)
                  case None =>
                    Effect[F].raiseError[FileMeta](
                      new Exception(s"Cannot insert or find FileMeta $fm", sqlex)
                    )
                }
            }

          val insert = get(chunk.fileId).flatMap {
            case Some(m) =>
              saveFileChunk(chunk).map(_ => Outcome.Created(m))
            case None =>
              val initial = FileMeta(
                chunk.fileId,
                Instant.now,
                Mimetype.unknown,
                0L,
                "",
                nChunks.toInt,
                chunksize
              )
              Stream.eval(tryInsertMeta(initial)).flatMap { meta =>
                saveFileChunk(chunk).map(_ => Outcome.Created(meta))
              }
          }

          insert.flatMap { m =>
            Stream.eval(stmt.countChunks(chunk.fileId).transact(xa)).flatMap { n =>
              val first = chunk.chunkNr == 0
              val last  = n == nChunks
              if (first && last) updateMime.drain ++ updateMeta.map(Outcome.Created.apply)
              else if (first) updateMime.map(Outcome.Created.apply)
              else if (last) updateMeta.map(Outcome.Created.apply)
              else Stream.emit(m)
            }
          }
      }

    def get(id: String): Stream[F, Option[FileMeta]] =
      Stream.eval(stmt.selectFileMeta(id).transact(xa))

    def fetchData(range: RangeDef): Pipe[F, FileMeta, Byte] = {
      def mkData(id: String, chunksLeft: Int, chunk: Int): Stream[F, ByteVector] =
        if (chunksLeft == 0) Stream.empty
        else
          Stream.eval(
            stmt
              .selectChunkData(id, Some(chunk).filter(_ > 0), Some(1))
              .unique
              .transact(xa)
          ) ++ mkData(id, chunksLeft - 1, chunk + 1)

      _.flatMap { fm =>
        range(fm) match {
          case Validated.Valid(Range.All) =>
            mkData(fm.id, fm.chunks, 0).through(Range.unchunk)

          case Validated.Valid(r: Range.ByteRange) =>
            mkData(
              fm.id,
              r.limit.getOrElse(fm.chunks - r.offset.getOrElse(0)),
              r.offset.getOrElse(0)
            ).through(Range.dropLeft(r))
              .through(Range.dropRight(r))
              .through(Range.unchunk)

          case Validated.Invalid(msg) =>
            Stream.raiseError(new Exception(s"Invalid range: $msg"))

          case Validated.Valid(Range.Empty) =>
            Stream.empty
        }
      }
    }

    def fetchData2(range: RangeDef): Pipe[F, FileMeta, Byte] =
      _.flatMap { fm =>
        range(fm) match {
          case Validated.Valid(Range.All) =>
            stmt
              .selectChunkData(fm.id)
              .streamWithChunkSize(1)
              .transact(xa)
              .through(Range.unchunk)

          case Validated.Valid(r: Range.ByteRange) =>
            r.select(
              stmt
                .selectChunkData(fm.id, r.offset, r.limit)
                .streamWithChunkSize(1)
                .transact(xa)
            )

          case Validated.Valid(Range.Empty) =>
            Stream.empty
          case Validated.Invalid(msg) =>
            Stream.raiseError(new Exception(s"Invalid range: $msg"))
        }
      }

    def exists(id: String): Stream[F, Boolean] =
      Stream.eval(F.map(stmt.fileExists(id).transact(xa))(_.isDefined))

    def delete(id: String): Stream[F, Boolean] = {
      val sql = for {
        n <- stmt.deleteChunks(id).run
        _ <- stmt.deleteFileMeta(id).run
      } yield n > 0
      Stream.eval(sql.transact(xa))
    }

    def count: Stream[F, Long] =
      Stream.eval(stmt.count.transact(xa))

    // low level
    def saveFileMeta(fm: FileMeta): Stream[F, Unit] =
      Stream.eval(stmt.insertFileMeta(fm).run.transact(xa)).map(_ => ())

    def saveFileChunk(fc: FileChunk): Stream[F, Unit] =
      Stream.eval(stmt.insertChunk(fc).run.transact(xa)).map(_ => ())

    def getChunks(
        id: String,
        offset: Option[Int] = None,
        limit: Option[Int] = None
    ): Stream[F, FileChunk] =
      stmt.selectChunks(id, offset, limit).streamWithChunkSize(1).transact(xa)

    def chunkExists(id: String, chunkNr: Long): Stream[F, Boolean] =
      Stream.eval(stmt.chunkExists(id, chunkNr).transact(xa))

    def chunkExistsRemove(
        id: String,
        chunkNr: Long,
        chunkLength: Long
    ): Stream[F, Boolean] =
      Stream.eval(stmt.chunkExistsRemove(id, chunkNr, chunkLength).transact(xa))

    private def rechunk(size: Int): Pipe[F, Byte, ByteVector] =
      _.chunkN(size).map(c => ByteVector.view(c.toArray))

    private def accumulateKey(
        time: Instant,
        chunkSize: Int,
        hint: MimetypeHint
    ): Pipe[F, FileChunk, FileMeta] =
      _.fold(
        (sha.newBuilder, FileMeta("", time, Mimetype.unknown, 0L, "", 0, chunkSize))
      )({
        case ((shab, m), chunk) =>
          val fm = m.incChunks(1).incLength(chunk.chunkLength) match {
            case nextFm if nextFm.chunks == 1 =>
              nextFm.copy(mimetype =
                config.mimetypeDetect.fromBytes(chunk.chunkData, hint)
              )
            case nextFm =>
              nextFm
          }
          (shab.update(chunk.chunkData), fm)
      }).map(t => t._2.copy(checksum = t._1.get))

    private def makeChecksum(id: String, nChunks: Int): Stream[F, String] =
      stmt
        .selectChunkData(id)
        .streamWithChunkSize(1)
        .transact(xa)
        .flatMap(bs => Stream.chunk(ByteVectorChunk(bs)))
        .through(fs2.hash.sha256)
        .chunks
        .map({
          case Chunk.ByteVectorChunk(bv) => bv.toHex
          case cs                        => ByteVector.view(cs.toArray).toHex
        })
        .foldMonoid
  }

}
