package bitpeace

import cats.data.Ior
import fs2.{Pipe, Stream, Handle, Pull, Chunk}
import scodec.bits.ByteVector

/**
  * A range for a chunk query.
  *
  * Specifies how many chunks and how many bytes of selected chunks have to be skipped.
  */
sealed trait Range

object Range {
  case object All extends Range
  case object Empty extends Range

  case class ByteRange(range: Ior[(Int, Int), (Int, Int)]) extends Range {
    def offset: Option[Int] = range.left.map(_._1)
    def dropL: Option[Int] = range.left.map(_._2).filter(_ > 0)
    def limit: Option[Int] = range.right.map(_._1)
    def dropR: Option[Int] = range.right.map(_._2).filter(_ > 0)
    def isEmpty = limit.exists(_ == 0)

    def select[F[_]](s: Stream[F, ByteVector]): Stream[F, Byte] = {
      s.through(dropLeft(this)).
        through(dropRight(this)).
        through(unchunk)
    }
  }

  def apply(range: Ior[(Int, Int), (Int, Int)]): Range =
    ByteRange(range)

  def dropRight[F[_]](r: Range): Pipe[F,ByteVector,ByteVector] =
    r match {
      case All => identity
      case Empty => identity
      case br: ByteRange =>
        br.dropR match {
          case None => identity
          case Some(n) =>
            _.through(mapLast(_.dropRight(n.toLong)))
        }
    }

  def dropLeft[F[_]](r: Range): Pipe[F,ByteVector,ByteVector] =
    r match {
      case All => identity
      case Empty => identity
      case br: ByteRange =>
        br.dropL match {
          case None => identity
          case Some(n) =>
            _.zipWithIndex.map {
              case (bv, 0) => bv.drop(n.toLong)
              case (bv, _) => bv
            }
        }
    }

  /** Apply `f` to the last element */
  def mapLast[F[_], I](f: I => I): Pipe[F, I, I] = {
    def go(last: Chunk[I]): Handle[F,I] => Pull[F,I,Unit] = {
      _.receiveOption {
        case Some((chunk, h)) => Pull.output(last) >> go(chunk)(h)
        case None =>
          val k = f(last(last.size-1))
          val init = last.take(last.size-1).toVector
          Pull.output(Chunk.indexedSeq(init :+ k))
      }
    }
    _.pull { _.receiveOption {
      case Some((c, h)) => go(c)(h)
      case None => Pull.done
    }}
  }

  def unchunk[F[_]]: Pipe[F, ByteVector, Byte] =
    _.flatMap(bv => Stream.chunk(Chunk.bytes(bv.toArray)))
}

// object range {
//   case class FileSettings(length: Long, chunkSize: Long)
//   type RangeSpec = FileSettings => Option[Range]

//   object RangeSpec {
//     val none: RangeSpec = _ => Range.empty
//     val all: RangeSpec = _ => Range.all

//     /** Calculating Range given the chunksize */
//     def bytes(offset: Option[Int], count: Option[Int]): RangeSpec = { case FileSettings(flen, chunkSize) =>
//       val left = offset.map { off =>
//         (off / chunkSize, off % chunkSize)
//       }
//       val right = count.flatMap { clen =>
//         val dropL = left.map(_._2).orEmpty
//         val rest = (offset.orEmpty + clen) % chunkSize

//         // when last chunk:
//         // |------x----y----|
//         // |------|      x = (offset + count) mod chunkSize
//         // |-----------| y = flen mod chunkSize
//         //        |----| dropR = y - x
//         val dropR = {
//           val onLastChunk = (offset.orEmpty + clen) / chunkSize == (flen.bytes / chunkSize)
//           val last =
//             if (onLastChunk) (flen % chunkSize) -1
//             else chunkSize
//           if (rest == 0) 0 else last - rest
//         }

//         // count = (chunkSize - dropL) + (chunkSize - dropR) + (chunkSize * (limit -2))
//         //         first row             last row              intermediate rows
//         // limit = ((count - (cs - dropL) - (cs -dropR)) / cs) + 2
//         val limit = ((clen - (chunkSize - dropL) - (chunkSize - dropR)) / chunkSize) + 2
//         if (dropR < 0) None
//         else Some((limit, dropR))
//       }
//       val outOfRange = (offset.orEmpty + count.orEmpty) > flen
//       if (outOfRange) None
//       else (left, right) match {
//         case (Some(l), Some(r)) => Some(Range(Ior.both(l, r)))
//         case (Some(l),       _) => Some(Range(Ior.left(l)))
//         case (_,       Some(r)) => Some(Range(Ior.right(r)))
//         case _ => None
//       }
//     }

//     def firstChunks(n: Int): RangeSpec = { settings =>
//       require (n > 0)
//       bytes(None, Some(n * settings.chunkSize.bytes))(settings)
//     }

//     val firstChunk: RangeSpec = firstChunks(1)

//     def firstBytes(n: Int): RangeSpec = {
//       require(n > 0)
//       bytes(None, Some(n))
//     }

//     /** From a range like a-b in bytes. It may also be -b or a-. */
//     def byteRange(value: Ior[Int, Int]): RangeSpec = {
//       value match {
//         case Ior.Left(a) =>
//           bytes(Some(a), None)
//         case Ior.Right(b) =>
//           bytes(None, Some(b))
//         case Ior.Both(a, b) =>
//           bytes(Some(a), Some(b -a))
//       }
//     }

//   }

// }
