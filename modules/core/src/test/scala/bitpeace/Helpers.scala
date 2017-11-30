package bitpeace

import java.io.InputStream
import fs2.{io, Pipe, Task, Stream, Chunk}
import scodec.bits.ByteVector

trait Helpers {

  def resource(name: String, chunksize: Int = 64 * 1024): Task[InputStream] = Task.delay {
    Option(getClass.getResourceAsStream(name)).get
  }

  def resourceStream(name: String, chunksize: Int = 64 * 1024): Stream[Task, Byte] =
    io.readInputStream[Task](resource(name), chunksize).
      // see https://github.com/functional-streams-for-scala/fs2/issues/1005
      chunks.flatMap(c => Stream.chunk(Chunk.bytes(c.toArray.clone)))

  def readBytes: Pipe[Task, Byte, ByteVector] =
    _.chunks.map(c => ByteVector.view(c.toArray)).
      fold1(_ ++ _)

  def toBase64: Pipe[Task, Byte, String] =
    _.chunks.map(c => ByteVector.view(c.toArray)).
      fold1(_ ++ _).
      map(_.toBase64)

  def readResource(name: String, chunksize: Int = 64 * 1024): ByteVector =
    resourceStream(name, chunksize).
      through(readBytes).
      runLog.
      unsafeRun.head

}
