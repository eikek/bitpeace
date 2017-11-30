package bitpeace

import scodec.bits.ByteVector
import minitest._

object TikaMimetypeDetectSpec extends SimpleTestSuite with Helpers {

  val tika = TikaMimetypeDetect

  test ("for bytes and names") {
    val bytes = resourceStream("/files/file.pdf").
      chunks.
      map(c => ByteVector.view(c.toArray)).
      runLog.
      unsafeRun.head

    assertEquals(tika.fromBytes(bytes, MimetypeHint.none) , Mimetype.`application/pdf`)
    assertEquals(tika.fromBytes(bytes, MimetypeHint.filename("file.pdf")) , Mimetype.`application/pdf`)
    assertEquals(tika.fromName("file.pdf", ""), Mimetype.`application/pdf`)
  }

}
