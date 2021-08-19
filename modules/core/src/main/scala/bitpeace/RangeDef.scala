package bitpeace

import cats.data.{Ior, Validated}
import cats.implicits._

/** When requsting a specific range of data, the offsets must be calculated given the
  * total size and chunksize.
  */
trait RangeDef extends (FileMeta => Validated[String, Range])

object RangeDef {
  def apply(f: FileMeta => Validated[String, Range]): RangeDef =
    new RangeDef { def apply(fm: FileMeta) = f(fm) }

  val none: RangeDef = RangeDef(_ => Validated.valid(Range.Empty))
  val all: RangeDef  = RangeDef(_ => Validated.valid(Range.All))

  /** Calculating Range given the chunksize */
  def bytes(offset: Option[Int], count: Option[Int]): RangeDef =
    RangeDef { fm =>
      val flen      = fm.length
      val chunkSize = fm.chunksize

      val left = offset.map(off => ((off / chunkSize).toInt, (off % chunkSize).toInt))
      val right = count.flatMap { clen =>
        val dropL = left.map(_._2).orEmpty
        val rest  = (offset.orEmpty + clen) % chunkSize

        // when last chunk:
        // |------x----y----|
        // |------|      x = (offset + count) mod chunkSize
        // |-----------| y = flen mod chunkSize
        //        |----| dropR = y - x
        val dropR = {
          val onLastChunk = (offset.orEmpty + clen) / chunkSize == (flen / chunkSize)
          val last =
            if (onLastChunk) (flen % chunkSize) - 1
            else chunkSize.toLong
          if (rest == 0) 0 else last - rest
        }

        // count = (chunkSize - dropL) + (chunkSize - dropR) + (chunkSize * (limit -2))
        //         first row             last row              intermediate rows
        // limit = ((count - (cs - dropL) - (cs -dropR)) / cs) + 2
        val limit = ((clen - (chunkSize - dropL) - (chunkSize - dropR)) / chunkSize) + 2
        if (dropR < 0) None
        else Some((limit.toInt, dropR.toInt))
      }
      val outOfRange = (offset.orEmpty + count.orEmpty) > flen
      if (outOfRange) Validated.invalid("Out of range")
      else
        (left, right) match {
          case (Some(l), Some(r)) => Validated.valid(Range(Ior.both(l, r)))
          case (Some(l), _)       => Validated.valid(Range(Ior.left(l)))
          case (_, Some(r))       => Validated.valid(Range(Ior.right(r)))
          case _                  => Validated.invalid("Invalid range specificatio")
        }
    }

  def firstChunks(n: Int): RangeDef =
    RangeDef { settings =>
      require(n > 0)
      bytes(None, Some(n * settings.chunksize.toInt))(settings)
    }

  val firstChunk: RangeDef = firstChunks(1)

  def firstBytes(n: Int): RangeDef = {
    require(n > 0)
    bytes(None, Some(n))
  }

  /** From a range like a-b in bytes. It may also be -b or a-. */
  def byteRange(value: Ior[Int, Int]): RangeDef =
    value match {
      case Ior.Left(a) =>
        bytes(Some(a), None)
      case Ior.Right(b) =>
        bytes(None, Some(b))
      case Ior.Both(a, b) =>
        bytes(Some(a), Some(b - a))
    }
}
