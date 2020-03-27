package bitpeace

import minitest._

object MimetypeSpec extends SimpleTestSuite {

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
}
