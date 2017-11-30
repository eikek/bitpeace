package bitpeace

import java.util.concurrent.CountDownLatch
import java.util.UUID

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cats.implicits._
import doobie.imports._
import fs2.Task
import fs2.interop.cats._
import scodec.bits.ByteVector

object BitpeaceSpec extends BitpeaceTestSuite {

  val makeBitpeace: Transactor[Task] => Bitpeace[Task] = xa => Bitpeace(config, xa)

  def chunkCount =
    sql"""SELECT count(*) from FileChunk""".query[Int].unique

  test ("add chunk in random order") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)

    val out1 = store.saveNew(data, chunksize, MimetypeHint.filename("file.pdf")).runLast.unsafeRun.get

    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex.map({ case (c, i) =>
      FileChunk(fileId, i, ByteVector.view(c.toArray))
    }).runLog.unsafeRun

    chunks.permutations.foreach { bs =>
      val id = UUID.randomUUID.toString
      val all = bs.
        map(ch => ch.copy(fileId = id)).
        map(ch => store.addChunk(ch, chunksize, out1.chunks, MimetypeHint.none)).
        reduce(_ ++ _).
        runLog.
        unsafeRun
      assertEquals(all.last.result.checksum, out1.checksum)
      assertEquals(all.last.result.mimetype, out1.mimetype)
      assertEquals(all.last.result.chunks, out1.chunks)
    }
  }

  test ("add chunk of small file") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 256 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)

    val out1 = store.saveNew(data, chunksize, MimetypeHint.filename("file.pdf")).runLast.unsafeRun.get
    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex.map({ case (c, i) =>
      FileChunk(fileId, i, ByteVector.view(c.toArray))
    }).runLog.unsafeRun

    assertEquals(chunks.size, 1)

    val out = store.addChunk(chunks(0), chunksize, 1, MimetypeHint.none).runLast.unsafeRun.get
    assertEquals(out.result.checksum, out1.checksum)
    assertEquals(out.result.mimetype, out1.mimetype)
    assertEquals(out.result.chunks, out1.chunks)
  }

  test ("add chunk in random order concurrently") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)

    val out1 = store.saveNew(data, chunksize, MimetypeHint.filename("file.pdf")).runLast.unsafeRun.get

    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex.map({ case (c, i) =>
      FileChunk(fileId, i, ByteVector.view(c.toArray))
    }).runLog.unsafeRun

    chunks.permutations.toVector.par.foreach { bs =>
      val id = UUID.randomUUID.toString
      val all = bs.toVector.
        map(ch => ch.copy(fileId = id)).
        par.
        map(ch => store.addChunk(ch, chunksize, out1.chunks, MimetypeHint.none).
          runLast.unsafeRun.get.result).
        foldLeft(Vector.empty[FileMeta])(_ :+ _)

      val last = all.find(_.length > 0).get
      assertEquals(last.checksum, out1.checksum)
      assertEquals(last.mimetype, out1.mimetype)
      assertEquals(last.chunks, out1.chunks)
    }
  }

  test ("add chunk that exists") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)

    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex.map({ case (c, i) =>
      FileChunk(fileId, i, ByteVector.view(c.toArray))
    }).runLog.unsafeRun


    val out1 = store.addChunk(chunks(0), chunksize, chunks.size, MimetypeHint.none).
      runLast.unsafeRun.get

    val out2 = store.addChunk(chunks(0), chunksize, chunks.size, MimetypeHint.none).
      runLast.unsafeRun.get

    assert(out1.isCreated)
    assert(out2.isUnmodified)
    assertEquals(out1.result, out2.result)
  }

  test ("save new file") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", 2048)

    val out1 = store.saveNew(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get
    assert(out1.id != out1.checksum)
    assertEquals(out1.checksum, "8fabb506346fc4b10e0e10f33ec0fa819038d701224ca63cbee7620c38b4736f")

    val out2 = store.saveNew(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get
    assert(out2.id != out2.checksum)
    assert(out1.id != out2.id)
    assertEquals(out2.checksum, out1.checksum)

    assertEquals(store.count.runLast.unsafeRun.orEmpty, 2)
    assertEquals(store.get(out1.id).unNoneTerminate.runLast.unsafeRun, Some(out1))
    assertEquals(store.get(out2.id).unNoneTerminate.runLast.unsafeRun, Some(out2))
  }

  test ("save a file") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", 2048)
    val out = store.save(data, chunksize, MimetypeHint.filename("file.pdf")).runLast.unsafeRun.get
    out match {
      case Outcome.Created(m) =>
        assertEquals(m.id, m.checksum)
        assertEquals(m.checksum, "8fabb506346fc4b10e0e10f33ec0fa819038d701224ca63cbee7620c38b4736f")
        assertEquals(m.chunks, 4)
        assertEquals(m.mimetype, Mimetype.`application/pdf`)
        assertEquals(m.length, 65404L)
      case r@Outcome.Unmodified(_) =>
        fail(s"Unexpected result: $r")
    }
    val fm = out.result

    val chunks = store.getChunks(fm.id).runLog.unsafeRun
    assertEquals(chunks.size, fm.chunks)
    chunks.foreach(c => assertEquals(c.fileId, fm.id))
    chunks.init.foreach(c => assertEquals(c.chunkLength, chunksize.toLong))
    assertEquals(chunks.last.chunkLength, 16252)
    assertEquals(chunks.foldLeft(0L)(_ + _.chunkLength), 65404)

    val bytesDb = store.get(fm.id).
      unNoneTerminate.
      through(store.fetchData2(RangeDef.all)).
      through(readBytes).
      runLast.
      unsafeRun.get
    val bytesFile = data.through(readBytes).runLast.unsafeRun.get
    val bytesChunk = chunks.map(_.chunkData).reduce(_ ++ _)

    assertEquals(bytesChunk, bytesFile)
    assertEquals(bytesDb, bytesFile)
    assertEquals(store.count.runLast.unsafeRun.orEmpty, 1)
  }

  test ("handle existing files") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)
    val out = store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get
    out match {
      case Outcome.Created(m) =>
        assertEquals(m.id, "8fabb506346fc4b10e0e10f33ec0fa819038d701224ca63cbee7620c38b4736f")
        assertEquals(m.chunks, 4)
        assertEquals(m.length, 65404L)
      case r@Outcome.Unmodified(_) =>
        fail(s"Unexpected result: $r")
    }
    val fm = out.result

    val out2 = store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get
    assertEquals(out2, Outcome.Unmodified(fm))
  }

  test ("save concurrently same file") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 32 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)
    val peng = new CountDownLatch(1)
    val f0 = Future {
      peng.await()
      store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get
    }
    val f1 = Future {
      peng.await()
      store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get
    }
    peng.countDown()
    val o0 = Await.result(f0, 5.seconds)
    val o1 = Await.result(f1, 5.seconds)

    assert(o0 != o1)
    assert(o0.isCreated || o1.isCreated)
    if (o0.isCreated) {
      assert(o1.isUnmodified)
    } else {
      assert(o0.isUnmodified)
    }
    assertEquals(o0.result, o1.result)
  }

  test ("load chunks") { xa =>
    import RangeDef.bytes

    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) = store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get

    def getBase64(r: RangeDef) =
      store.get(fm.id).
        unNoneTerminate.
        through(store.fetchData2(r)).
        through(toBase64).
        runLast.unsafeRun.get

    assertEquals(
      getBase64(bytes(None, Some(80))),
      "JVBERi0xLjUKJdDUxdgKMTAgMCBvYmoKPDwKL0xlbmd0aCAyNjMgICAgICAgCi9GaWx0ZXIgL0ZsYXRlRGVjb2RlCj4+CnN0cmVhbQp42m0=")
    assertEquals(
      getBase64(bytes(Some(3500), Some(80))),
      "3cNffssNBJtMRzfLcgH1ZcU2oG/jomzHp36AIrrZj/sKimkjyHQXTeauUbnWnsIJ5Ub8zb59xvW6fLSnkqaOlgJXKXyVD0gJYFUFBvRJYvQ=")
    assertEquals(
      getBase64(bytes(Some(16344), Some(80))),
      "uL7wCy2XxucsUna47LombVa37iCz2RwEaBu2vq//4bxtPpRUDXfyww3E0LtPykG8MCG5SoO7kFmGJYLATDY8pi96l+qdvEFJvlJPRPfonwg=")
  }

  test ("exists") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) = store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get

    assert(store.exists(fm.id).runLast.unsafeRun.get)
    assert(! store.exists("abc").runLast.unsafeRun.get)
  }

  test ("delete") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) = store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get

    assert(store.delete(fm.id).runLast.unsafeRun.get)
    assert(!store.delete(fm.id).runLast.unsafeRun.get)

    assertEquals(chunkCount.transact(xa).unsafeRun, 0)
  }

  test ("remove partial chunk") { xa =>
    val store = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) = store.save(data, chunksize, MimetypeHint.none).runLast.unsafeRun.get

    assertEquals(chunkCount.transact(xa).unsafeRun, 4)
    assert(store.chunkExistsRemove(fm.id, 2, 16 * 1024).runLast.unsafeRun.get)
    assert(store.chunkExistsRemove(fm.id, 2, 16 * 1024).runLast.unsafeRun.get)

    assert(! store.chunkExistsRemove(fm.id, 2, 8 * 1024).runLast.unsafeRun.get)
    assert(! store.chunkExists(fm.id, 2).runLast.unsafeRun.get)
    assertEquals(chunkCount.transact(xa).unsafeRun, 3)
  }
}
