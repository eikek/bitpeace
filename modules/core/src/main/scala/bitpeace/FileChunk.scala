package bitpeace

import scodec.bits.ByteVector

/** A chunk of data.
  *
  * The first chunk has chunkNr = 0
  */
final case class FileChunk(fileId: String, chunkNr: Long, chunkData: ByteVector) {

  lazy val chunkLength: Long = chunkData.length.toLong

}
