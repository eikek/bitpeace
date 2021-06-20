package bitpeace

import munit._

class MimetypeSpec extends FunSuite {

  test("reading/parsing") {
    val mtStr =
      """application/vnd.oasis.opendocument.spreadsheet; name="=?UTF-8?Q?B=C3=BCcherVZ=2Eods?=""""
    val mt = Mimetype.parse(mtStr).fold(throw _, identity)
    assertEquals(mt.primary, "application")
    assertEquals(mt.sub, "vnd.oasis.opendocument.spreadsheet")
    assertEquals(mt.param("name"), Some("=?UTF-8?Q?B=C3=BCcherVZ=2Eods?="))

    val mtStr2 = mt.asString
    assertEquals(mtStr2, mtStr)
  }

  test("matches") {
    val textAll = Mimetype.text("*")
    assert(textAll.matches(Mimetype.textHtml))
    assert(Mimetype.textHtml.matches(Mimetype.textHtml))
    assert(!textAll.matches(Mimetype.applicationPdf))
  }
}
