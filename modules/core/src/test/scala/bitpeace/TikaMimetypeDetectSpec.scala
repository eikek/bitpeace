package bitpeace

import cats.effect.unsafe.implicits.global
import minitest._
import scodec.bits.ByteVector

object TikaMimetypeDetectSpec extends SimpleTestSuite with Helpers {

  val tika = TikaMimetypeDetect

  test("for bytes and names") {
    val bytes = resourceStream("/files/file.pdf").chunks
      .map(c => ByteVector.view(c.toArray))
      .compile
      .toVector
      .unsafeRunSync()
      .head

    assertEquals(tika.fromBytes(bytes, MimetypeHint.none), Mimetype.`application/pdf`)
    assertEquals(
      tika.fromBytes(bytes, MimetypeHint.filename("file.pdf")),
      Mimetype.`application/pdf`
    )
    assertEquals(tika.fromName("file.pdf", ""), Mimetype.`application/pdf`)
  }

}
