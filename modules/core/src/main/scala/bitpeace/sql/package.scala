package bitpeace

import java.time.Instant
import doobie.imports._
import scodec.bits.ByteVector

package object sql {
  implicit val bvMeta: Meta[ByteVector] =
    Meta[Array[Byte]].xmap(
      ar => ByteVector.view(ar),
      bv => bv.toArray
    )

  implicit val mimetypeMeta: Meta[Mimetype] =
    Meta[String].xmap(Mimetype.parse(_).fold(ex => throw ex, identity), _.asString)

  implicit val instantMeta: Meta[Instant] =
    Meta[String].xmap(Instant.parse, _.toString)
}
