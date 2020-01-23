package bitpeace

import java.time._

case class FileMeta(
    id: String,
    timestamp: Instant,
    mimetype: Mimetype,
    length: Long,
    checksum: String,
    chunks: Int,
    chunksize: Int
) {

  def incLength(n: Long) = copy(length = length + n)
  def incChunks(n: Int)  = copy(chunks = chunks + n)
}
