package bitpeace

import cats.effect._
import doobie._
import doobie.implicits._
import bitpeace.sql._

import java.nio.file.{Files, Paths}
import java.sql._

trait DB[F[_]] {

  def tx(db: String): Transactor[F]

  def dropDatabase(db: String, tx: Transactor[F]): F[Unit]

  def dbms: Dbms
}

object DB {

  object H2 extends DB[IO] {

    def tx(db: String): Transactor[IO] = {
      val file = Paths.get(s"target/${db}").toAbsolutePath
      Files.createDirectories(file.getParent)

      Transactor.fromDriverManager[IO](
        "org.h2.Driver", s"jdbc:h2:${file}", "sa", ""
      )
    }

    def dropDatabase(db: String, xa: Transactor[IO]) =
      for {
        file <- IO(Paths.get(s"target/${db}").toAbsolutePath)
        _   <- sql"drop all objects delete files;".update.run.transact(xa)
        _   <- IO(Files.list(file.getParent).
          filter(Scala211.predicate(p => p.getFileName.toString.startsWith(file.getFileName.toString))).
          forEach(Scala211.consume(p => Files.deleteIfExists(p))))
      } yield ()

    val dbms = Dbms.H2
  }


  object Postgres extends DB[IO] {
    Class.forName("org.postgresql.Driver")

    def tx(db: String): Transactor[IO] = {
      val conn = DriverManager.getConnection("jdbc:postgresql://localhost/postgres", "dev", "dev");
      val statement = conn.createStatement();
      statement.executeUpdate(s"CREATE DATABASE $db");
      conn.close()

      Transactor.fromDriverManager[IO](
        "org.postgresql.Driver", s"jdbc:postgresql://localhost/$db", "dev", "dev"
      )
    }

    def dropDatabase(db: String, xa: Transactor[IO]) = IO {
      val conn = DriverManager.getConnection("jdbc:postgresql://localhost/postgres", "dev", "dev");
      val statement = conn.createStatement();
      statement.executeUpdate(s"DROP DATABASE $db");
      conn.close()
    }

    val dbms = Dbms.Postgres
  }


  private object Scala211 {
    def predicate[A](p: A => Boolean): java.util.function.Predicate[A] =
      new java.util.function.Predicate[A] {
        def test(a: A): Boolean = p(a)
      }

    def consume[A](c: A => Unit): java.util.function.Consumer[A] =
      new java.util.function.Consumer[A] {
        def accept(a: A) = c(a)
      }
  }
}
