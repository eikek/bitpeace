package bitpeace

import java.util.UUID
import java.util.concurrent.CountDownLatch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import fs2._
import scodec.bits.ByteVector

object BitpeaceSpec extends BitpeaceTestSuite {
  def makeBitpeace(p: DbSetup): Bitpeace[IO] =
    Bitpeace(config, p.xa)

//  override val dbSetup = DB.Postgres

  def chunkCount =
    sql"""SELECT count(*) from FileChunk""".query[Int].unique

  test("save new with id") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)

    val out = store
      .saveNew(data, chunksize, MimetypeHint.filename("file.pdf"), fileId = Some("abc"))
      .compile
      .last
      .unsafeRunSync()
      .get
    assertEquals(out.id, "abc")
  }

  test("add chunks unoreded sequentially") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)

    val out1 = store
      .saveNew(data, chunksize, MimetypeHint.filename("file.pdf"))
      .compile
      .last
      .unsafeRunSync()
      .get

    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex
      .map({ case (c, i) =>
        FileChunk(fileId, i, ByteVector.view(c.toArray))
      })
      .compile
      .toVector
      .unsafeRunSync()

    chunks.permutations.foreach { bs =>
      val id = UUID.randomUUID.toString
      val all = bs
        .map(ch => ch.copy(fileId = id))
        .map(ch => store.addChunk(ch, chunksize, out1.chunks, MimetypeHint.none))
        .reduce(_ ++ _)
        .compile
        .toVector
        .unsafeRunSync()
      assertEquals(all.last.result.checksum, out1.checksum)
      assertEquals(all.last.result.mimetype, out1.mimetype)
      assertEquals(all.last.result.chunks, out1.chunks)
    }
  }

  test("add chunk of small file") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 256 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)

    val out1 = store
      .saveNew(data, chunksize, MimetypeHint.filename("file.pdf"))
      .compile
      .last
      .unsafeRunSync()
      .get
    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex
      .map({ case (c, i) =>
        FileChunk(fileId, i, ByteVector.view(c.toArray))
      })
      .compile
      .toVector
      .unsafeRunSync()

    assertEquals(chunks.size, 1)

    val out =
      store
        .addChunk(chunks(0), chunksize, 1, MimetypeHint.none)
        .compile
        .last
        .unsafeRunSync()
        .get
    assertEquals(out.result.checksum, out1.checksum)
    assertEquals(out.result.mimetype, out1.mimetype)
    assertEquals(out.result.chunks, out1.chunks)
  }

  test("add chunk in random order concurrently") { p =>
    val store     = makeBitpeace(p)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)

    val out1: FileMeta = store
      .saveNew(data, chunksize, MimetypeHint.filename("file.pdf"))
      .compile
      .last
      .unsafeRunSync()
      .get

    val chunks = data.chunks.zipWithIndex
      .map({ case (c, i) =>
        FileChunk("fileabc", i, ByteVector.view(c.toArray))
      })
      .compile
      .toVector
      .unsafeRunSync()

    val prg = Stream.emits(chunks.permutations.toVector).covary[IO].evalMap { bs =>
      val id  = UUID.randomUUID.toString
      val all = bs.map(ch => ch.copy(fileId = id))

      Stream
        .emits(all)
        .covary[IO]
        .parEvalMapUnordered(4) { ch =>
          store
            .addChunk(ch, chunksize, out1.chunks, MimetypeHint.none)
            .compile
            .last
            .map(_.get.result)
        }
        .compile
        .toVector
        .unsafeRunSync()

      val fm = store.get(id).compile.lastOrError.unsafeRunSync().get

      assertEquals(fm.checksum, out1.checksum)
      assertEquals(fm.mimetype, out1.mimetype)
      assertEquals(fm.chunks, out1.chunks)

      IO(true)
    }

    prg.compile.drain.unsafeRunSync()
  }

  test("add chunk that exists") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)

    val fileId = "fileabc"
    val chunks = data.chunks.zipWithIndex
      .map({ case (c, i) =>
        FileChunk(fileId, i, ByteVector.view(c.toArray))
      })
      .compile
      .toVector
      .unsafeRunSync()

    val out1 = store
      .addChunk(chunks(0), chunksize, chunks.size, MimetypeHint.none)
      .compile
      .last
      .unsafeRunSync()
      .get

    val out2 = store
      .addChunk(chunks(0), chunksize, chunks.size, MimetypeHint.none)
      .compile
      .last
      .unsafeRunSync()
      .get

    assert(out1.isCreated)
    assert(out2.isUnmodified)
    assertEquals(out1.result, out2.result)
  }

  test("save new file") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", 2048)

    val out1 =
      store.saveNew(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get
    assert(out1.id != out1.checksum)
    assertEquals(
      out1.checksum,
      "8fabb506346fc4b10e0e10f33ec0fa819038d701224ca63cbee7620c38b4736f"
    )

    val out2 =
      store.saveNew(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get
    assert(out2.id != out2.checksum)
    assert(out1.id != out2.id)
    assertEquals(out2.checksum, out1.checksum)

    assertEquals(store.count.compile.last.unsafeRunSync().orEmpty, 2)
    assertEquals(
      store.get(out1.id).unNoneTerminate.compile.last.unsafeRunSync(),
      Some(out1)
    )
    assertEquals(
      store.get(out2.id).unNoneTerminate.compile.last.unsafeRunSync(),
      Some(out2)
    )
  }

  test("save a file") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", 2048)
    val out =
      store
        .save(data, chunksize, MimetypeHint.filename("file.pdf"))
        .compile
        .last
        .unsafeRunSync()
        .get
    out match {
      case Outcome.Created(m) =>
        assertEquals(m.id, m.checksum)
        assertEquals(
          m.checksum,
          "8fabb506346fc4b10e0e10f33ec0fa819038d701224ca63cbee7620c38b4736f"
        )
        assertEquals(m.chunks, 4)
        assertEquals(m.mimetype, Mimetype.`application/pdf`)
        assertEquals(m.length, 65404L)
      case r @ Outcome.Unmodified(_) =>
        fail(s"Unexpected result: $r")
    }
    val fm = out.result

    val chunks = store.getChunks(fm.id).compile.toVector.unsafeRunSync()
    assertEquals(chunks.size, fm.chunks)
    chunks.foreach(c => assertEquals(c.fileId, fm.id))
    chunks.init.foreach(c => assertEquals(c.chunkLength, chunksize.toLong))
    assertEquals(chunks.last.chunkLength, 16252)
    assertEquals(chunks.foldLeft(0L)(_ + _.chunkLength), 65404)

    val bytesDb = store
      .get(fm.id)
      .unNoneTerminate
      .through(store.fetchData2(RangeDef.all))
      .through(readBytes)
      .compile
      .last
      .unsafeRunSync()
      .get
    val bytesFile  = data.through(readBytes).compile.last.unsafeRunSync().get
    val bytesChunk = chunks.map(_.chunkData).reduce(_ ++ _)

    assertEquals(bytesChunk, bytesFile)
    assertEquals(bytesDb, bytesFile)
    assertEquals(store.count.compile.last.unsafeRunSync().orEmpty, 1)
  }

  test("handle existing files") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)
    val out =
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get
    out match {
      case Outcome.Created(m) =>
        assertEquals(
          m.id,
          "8fabb506346fc4b10e0e10f33ec0fa819038d701224ca63cbee7620c38b4736f"
        )
        assertEquals(m.chunks, 4)
        assertEquals(m.length, 65404L)
      case r @ Outcome.Unmodified(_) =>
        fail(s"Unexpected result: $r")
    }
    val fm = out.result

    val out2 =
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get
    assertEquals(out2, Outcome.Unmodified(fm))
  }

  test("save concurrently same file") { xa =>
    val store     = makeBitpeace(xa)
    val chunksize = 32 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)
    val peng      = new CountDownLatch(1)
    val f0 = Future {
      peng.await()
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get
    }
    val f1 = Future {
      peng.await()
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get
    }
    peng.countDown()
    val o0 = Await.result(f0, 5.seconds)
    val o1 = Await.result(f1, 5.seconds)

    assert(o0 != o1)
    assert(o0.isCreated || o1.isCreated)
    if (o0.isCreated)
      assert(o1.isUnmodified)
    else
      assert(o0.isUnmodified)
    assertEquals(o0.result, o1.result)
  }

  test("load chunks") { xa =>
    import RangeDef.bytes

    val store     = makeBitpeace(xa)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) =
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get

    def getBase64(r: RangeDef) =
      store
        .get(fm.id)
        .unNoneTerminate
        .through(store.fetchData2(r))
        .through(toBase64)
        .compile
        .last
        .unsafeRunSync()
        .get

    assertEquals(
      getBase64(bytes(None, Some(80))),
      "JVBERi0xLjUKJdDUxdgKMTAgMCBvYmoKPDwKL0xlbmd0aCAyNjMgICAgICAgCi9GaWx0ZXIgL0ZsYXRlRGVjb2RlCj4+CnN0cmVhbQp42m0="
    )
    assertEquals(
      getBase64(bytes(Some(3500), Some(80))),
      "3cNffssNBJtMRzfLcgH1ZcU2oG/jomzHp36AIrrZj/sKimkjyHQXTeauUbnWnsIJ5Ub8zb59xvW6fLSnkqaOlgJXKXyVD0gJYFUFBvRJYvQ="
    )
    assertEquals(
      getBase64(bytes(Some(16344), Some(80))),
      "uL7wCy2XxucsUna47LombVa37iCz2RwEaBu2vq//4bxtPpRUDXfyww3E0LtPykG8MCG5SoO7kFmGJYLATDY8pi96l+qdvEFJvlJPRPfonwg="
    )
  }

  test("exists") { p =>
    val store     = makeBitpeace(p)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) =
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get

    assert(store.exists(fm.id).compile.last.unsafeRunSync().get)
    assert(!store.exists("abc").compile.last.unsafeRunSync().get)
  }

  test("delete") { p =>
    val store     = makeBitpeace(p)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) =
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get

    assert(store.delete(fm.id).compile.last.unsafeRunSync().get)
    assert(!store.delete(fm.id).compile.last.unsafeRunSync().get)

    assertEquals(chunkCount.transact(p.xa).unsafeRunSync(), 0)
  }

  test("remove partial chunk") { p =>
    val store     = makeBitpeace(p)
    val chunksize = 16 * 1024
    val data      = resourceStream("/files/file.pdf", chunksize)
    val Outcome.Created(fm) =
      store.save(data, chunksize, MimetypeHint.none).compile.last.unsafeRunSync().get

    assertEquals(chunkCount.transact(p.xa).unsafeRunSync(), 4)
    assert(store.chunkExistsRemove(fm.id, 2, 16 * 1024).compile.last.unsafeRunSync().get)
    assert(store.chunkExistsRemove(fm.id, 2, 16 * 1024).compile.last.unsafeRunSync().get)

    assert(!store.chunkExistsRemove(fm.id, 2, 8 * 1024).compile.last.unsafeRunSync().get)
    assert(!store.chunkExists(fm.id, 2).compile.last.unsafeRunSync().get)
    assertEquals(chunkCount.transact(p.xa).unsafeRunSync(), 3)
  }
}
