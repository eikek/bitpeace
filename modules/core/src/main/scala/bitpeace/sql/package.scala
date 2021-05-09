package bitpeace

import java.time.Instant

import doobie._
import scodec.bits.ByteVector

package object sql {
  implicit val bvMeta: Meta[ByteVector] =
    Meta[Array[Byte]].imap(ar => ByteVector.view(ar))(bv => bv.toArray)

  implicit val mimetypeMeta: Meta[Mimetype] =
    Meta[String].imap(Mimetype.parse(_).fold(ex => throw ex, identity))(_.asString)

  implicit val instantMeta: Meta[Instant] =
    Meta[String].imap(Instant.parse)(_.toString)
}
