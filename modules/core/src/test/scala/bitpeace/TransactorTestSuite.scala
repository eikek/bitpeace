package bitpeace

import java.nio.file.{Files, Paths, Path}
import java.util.UUID

import minitest._
import doobie._, doobie.implicits._
import cats.effect._
import scala.concurrent.ExecutionContext

trait TransactorTestSuite extends TestSuite[(Path, Transactor[IO])] with Helpers {

  implicit val testContextShift: ContextShift[IO] = TransactorTestSuite.testContextShift

  def setup(): (Path, Transactor[IO]) = {
    val fname = UUID.randomUUID.toString
    val file = Paths.get(s"target/${fname}").toAbsolutePath
    Files.createDirectories(file.getParent)
    (file, H2.tx(file.toString))
  }

  def tearDown(p: (Path, Transactor[IO])): Unit = {
    val (file, xa) = p
    H2.dropDatabase.run.transact(xa).unsafeRunSync

    Files.list(file.getParent).
      filter(p => p.getFileName.toString.startsWith(file.getFileName.toString)).
      forEach(p => Files.deleteIfExists(p))
  }

}

object TransactorTestSuite {
  implicit val testContextShift = IO.contextShift(ExecutionContext.global)
}
