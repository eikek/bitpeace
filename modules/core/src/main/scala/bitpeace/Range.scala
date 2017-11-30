package bitpeace

import cats.data.Ior
import fs2.{Pipe, Stream, Chunk}
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
  def mapLast[F[_], I](f: I => I): Pipe[F, I, I] =
    s => s.dropLast ++ s.last.unNoneTerminate.map(f)

  def unchunk[F[_]]: Pipe[F, ByteVector, Byte] =
    _.flatMap(bv => Stream.chunk(Chunk.bytes(bv.toArray)))
}
