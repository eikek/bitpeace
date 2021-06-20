package bitpeace

import java.time.Instant

import bitpeace.RangeDef._
import cats.data.{Ior, Validated}
import munit._

class RangeDefSpec extends FunSuite {

  def fs(len: Long, chunkSize: Long): FileMeta =
    FileMeta("id", Instant.now, Mimetype.unknown, len, "", 22, chunkSize.toInt)
  def fs(chunkSize: Long): FileMeta = fs(30.mbytes, chunkSize)

  test("calculate correct boundaries") {
    assertEquals(
      bytes(Some(80), Some(210))(fs(100.bytes)),
      Validated.valid(Range(Ior.both(0 -> 80, 3 -> 10)))
    )
    assertEquals(
      bytes(Some(50), Some(100))(fs(100.bytes)),
      Validated.valid(Range(Ior.both(0 -> 50, 2 -> 50)))
    )
    assertEquals(
      bytes(Some(680), None)(fs(250.bytes)),
      Validated.valid(Range(Ior.left(2 -> 180)))
    )
    assertEquals(
      bytes(Some(680), Some(1212))(fs(250.bytes)),
      Validated.valid(Range(Ior.both(2 -> 180, 6 -> 108)))
    )
    assertEquals(
      bytes(Some(5 * 1024 * 1020), Some(6 * 1024 * 1002))(fs(256.kbytes)),
      Validated.valid(Range(Ior.both(19 -> 241664, 25 -> 155648)))
    )
    assertEquals(
      bytes(Some(500), Some(100))(fs(15000.bytes)),
      Validated.valid(Range(Ior.both(0 -> 500, 1 -> 14400)))
    )
    assertEquals(
      bytes(None, Some(1024))(fs(15000.bytes)),
      Validated.valid(Range(Ior.right(1 -> 13976)))
    )
    assertEquals(
      bytes(None, Some(15000))(fs(15000.bytes)),
      Validated.valid(Range(Ior.right(1 -> 0)))
    )
  }

  test("calculate correct boundaries at end of data") {
    assertEquals(
      byteRange(Ior.both(22, 28))(fs(28.bytes, 10.bytes)),
      Validated.valid(Range(Ior.left((2, 2))))
    )

    // length = 6773039
    assertEquals(
      byteRange(Ior.both(6707503, 6773038))(fs(6773039.bytes, 256.kbytes)),
      Validated.valid(Range(Ior.both((25, 153903), (1, 0))))
    )

    assertEquals(
      byteRange(Ior.both(6553600, 6707502))(fs(6773039.bytes, 256.kbytes)),
      Validated.valid(Range(Ior.both((25, 0), (1, 65536))))
    )

    assertEquals(
      byteRange(Ior.both(6269010, 6707502))(fs(6773039.bytes, 256.kbytes)),
      Validated.valid(Range(Ior.both((23, 239698), (2, 65536))))
    )
  }

  test("requesting outside of length") {
    assertEquals(
      byteRange(Ior.both(22, 36))(fs(28.bytes, 10.bytes)),
      Validated.invalid("Out of range")
    )
  }

  implicit class SizeOps(n: Int) {
    def bytes  = n.toLong
    def kbytes = 1024 * bytes
    def mbytes = 1024 * kbytes
  }
}
