package bitpeace

import java.util.UUID

import cats.effect.IO
import doobie._
import scodec.bits.ByteVector

object BitpeaceSpec212 extends BitpeaceTestSuite {
  val makeBitpeace: Transactor[IO] => Bitpeace[IO] = xa => Bitpeace(config, xa)

  test ("add chunk in random order concurrently") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)

    val out1 = store.saveNew(data, chunksize, MimetypeHint.filename("file.pdf")).compile.last.unsafeRunSync.get

    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex.map({ case (c, i) =>
      FileChunk(fileId, i, ByteVector.view(c.toArray))
    }).compile.toVector.unsafeRunSync

    chunks.permutations.toVector.par.foreach { bs =>
      val id = UUID.randomUUID.toString
      val all = bs.toVector.
        map(ch => ch.copy(fileId = id)).
        par.
        map(ch => store.addChunk(ch, chunksize, out1.chunks, MimetypeHint.none).
          compile.last.unsafeRunSync.get.result).
        foldLeft(Vector.empty[FileMeta])(_ :+ _)

      val last = all.find(_.length > 0).getOrElse(sys.error("No chunk with a length found"))
      assertEquals(last.checksum, out1.checksum)
      assertEquals(last.mimetype, out1.mimetype)
      assertEquals(last.chunks, out1.chunks)
    }
  }

}
