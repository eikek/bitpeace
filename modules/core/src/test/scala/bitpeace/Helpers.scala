package bitpeace

import java.io.InputStream

import cats.effect._
import cats.effect.unsafe.implicits.global
import fs2._
import scodec.bits.ByteVector

trait Helpers {

  def resource(name: String): IO[InputStream] =
    IO {
      Option(getClass.getResourceAsStream(name)).get
    }

  def resourceStream(name: String, chunksize: Int = 64 * 1024): Stream[IO, Byte] =
    io.readInputStream[IO](resource(name), chunksize)

  def readBytes: Pipe[IO, Byte, ByteVector] =
    _.chunks.map(c => ByteVector.view(c.toArray)).fold1(_ ++ _)

  def toBase64: Pipe[IO, Byte, String] =
    _.chunks.map(c => ByteVector.view(c.toArray)).fold1(_ ++ _).map(_.toBase64)

  def readResource(name: String, chunksize: Int = 64 * 1024): ByteVector =
    resourceStream(name, chunksize)
      .through(readBytes)
      .compile
      .toVector
      .unsafeRunSync()
      .head

}
