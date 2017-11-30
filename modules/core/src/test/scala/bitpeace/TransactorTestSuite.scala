package bitpeace

import java.nio.file.{Files, Paths}

import minitest._
import fs2.Task
import fs2.interop.cats._
import doobie.imports._

trait TransactorTestSuite extends TestSuite[Transactor[Task]] with Helpers {

  def setup(): Transactor[Task] = {
    val file = Paths.get(s"target/${getClass.getSimpleName}").toAbsolutePath
    Files.createDirectories(file.getParent)
    H2.tx(file.toString)
  }

  def tearDown(xa: Transactor[Task]): Unit = {
    H2.dropDatabase.run.transact(xa).unsafeRun
  }

}
