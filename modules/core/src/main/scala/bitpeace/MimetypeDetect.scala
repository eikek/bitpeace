package bitpeace

import scodec.bits.ByteVector

/** Detect content type given bytes and optionally some hints like
  * filename or advertised mimetype.
  */
trait MimetypeDetect {

  def fromBytes(bv: ByteVector, hint: MimetypeHint): Mimetype

  def fromName(filename: String, advertised: String = ""): Mimetype

}

object MimetypeDetect {
  val none = new MimetypeDetect {
    def fromBytes(bv: ByteVector, hint: MimetypeHint): Mimetype       = Mimetype.unknown
    def fromName(filename: String, advertised: String = ""): Mimetype = Mimetype.unknown
  }
}
