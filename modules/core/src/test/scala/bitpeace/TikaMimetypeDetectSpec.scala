package bitpeace

import cats.effect.unsafe.implicits.global
import munit._
import scodec.bits.ByteVector

class TikaMimetypeDetectSpec extends FunSuite with Helpers {

  val tika = TikaMimetypeDetect

  test("for bytes and names") {
    val bytes = resourceStream("/files/file.pdf").chunks
      .map(c => ByteVector.view(c.toArray))
      .compile
      .toVector
      .unsafeRunSync()
      .head

    assertEquals(tika.fromBytes(bytes, MimetypeHint.none), Mimetype.applicationPdf)
    assertEquals(
      tika.fromBytes(bytes, MimetypeHint.filename("file.pdf")),
      Mimetype.applicationPdf
    )
    assertEquals(tika.fromName("file.pdf", ""), Mimetype.applicationPdf)
  }

}
