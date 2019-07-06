package bitpeace

import java.io.InputStream
import cats.effect._
import fs2.{io, Pipe, Stream, Chunk}
import scodec.bits.ByteVector
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import TransactorTestSuite.testContextShift

trait Helpers {

  def resource(name: String, chunksize: Int = 64 * 1024): IO[InputStream] = IO {
    Option(getClass.getResourceAsStream(name)).get
  }

  def resourceStream(name: String, chunksize: Int = 64 * 1024): Stream[IO, Byte] =
    io.readInputStream[IO](resource(name), chunksize, Helpers.blocker).
      // see https://github.com/functional-streams-for-scala/fs2/issues/1005
      chunks.flatMap(c => Stream.chunk(Chunk.bytes(c.toArray.clone)))

  def readBytes: Pipe[IO, Byte, ByteVector] =
    _.chunks.map(c => ByteVector.view(c.toArray)).
      fold1(_ ++ _)

  def toBase64: Pipe[IO, Byte, String] =
    _.chunks.map(c => ByteVector.view(c.toArray)).
      fold1(_ ++ _).
      map(_.toBase64)

  def readResource(name: String, chunksize: Int = 64 * 1024): ByteVector =
    resourceStream(name, chunksize).
      through(readBytes).
      compile.toVector.
      unsafeRunSync.head

}

object Helpers {

  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(3))

  val blocker = Blocker.liftExecutionContext(blockingEc)
}
