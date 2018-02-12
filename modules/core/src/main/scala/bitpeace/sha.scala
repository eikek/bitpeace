package bitpeace

import java.security.MessageDigest
import scodec.bits.ByteVector

private[bitpeace] object sha {

  private def hex(bytes: Array[Byte]): String =
    ByteVector.view(bytes).toHex

  def newBuilder = new ShaBuilder
  final class ShaBuilder {
    private val digest = MessageDigest.getInstance("SHA-256")
    def update(data: ByteVector): ShaBuilder =
      update(data.toArray)

    def update(data: Array[Byte]): ShaBuilder = {
      digest.update(data)
      this
    }

    def get: String = hex(digest.digest())
  }
}
