package bitpeace

import java.nio.file.{Files, Paths}

import minitest._
import doobie._, doobie.implicits._
import cats.effect._
import scala.concurrent.ExecutionContext

trait TransactorTestSuite extends TestSuite[Transactor[IO]] with Helpers {

  implicit val testContextShift: ContextShift[IO] = TransactorTestSuite.testContextShift

  def setup(): Transactor[IO] = {
    val file = Paths.get(s"target/${getClass.getSimpleName}").toAbsolutePath
    Files.createDirectories(file.getParent)
    H2.tx(file.toString)
  }

  def tearDown(xa: Transactor[IO]): Unit = {
    H2.dropDatabase.run.transact(xa).unsafeRunSync
  }

}

object TransactorTestSuite {
  implicit val testContextShift = IO.contextShift(ExecutionContext.global)
}
