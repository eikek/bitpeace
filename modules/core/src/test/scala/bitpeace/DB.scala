package bitpeace

import cats.effect._
import doobie._
import doobie.implicits._
import bitpeace.sql._

import java.nio.file.{Files, Paths}
import java.sql._

trait DB[F[_]] {

  def tx(db: String)(implicit C: ContextShift[F]): Transactor[F]

  def dropDatabase(db: String, tx: Transactor[F])(implicit C: ContextShift[IO]): F[Unit]

  def dbms: Dbms
}

object DB {

  object H2 extends DB[IO] {

    def tx(db: String)(implicit C: ContextShift[IO]): Transactor[IO] = {
      val file = Paths.get(s"target/${db}").toAbsolutePath
      Files.createDirectories(file.getParent)

      Transactor.fromDriverManager[IO](
        "org.h2.Driver", s"jdbc:h2:${file}", "sa", ""
      )
    }

    def dropDatabase(db: String, xa: Transactor[IO])(implicit C: ContextShift[IO]) =
      for {
        file <- IO(Paths.get(s"target/${db}").toAbsolutePath)
        _   <- sql"drop all objects delete files;".update.run.transact(xa)
        _   <- IO(Files.list(file.getParent).
          filter(p => p.getFileName.toString.startsWith(file.getFileName.toString)).
          forEach(p => Files.deleteIfExists(p)))
      } yield ()

    val dbms = Dbms.H2
  }


  object Postgres extends DB[IO] {
    Class.forName("org.postgresql.Driver")

    def tx(db: String)(implicit C: ContextShift[IO]): Transactor[IO] = {
      val conn = DriverManager.getConnection("jdbc:postgresql://localhost/postgres", "dev", "dev");
      val statement = conn.createStatement();
      statement.executeUpdate(s"CREATE DATABASE $db");
      conn.close()

      Transactor.fromDriverManager[IO](
        "org.postgresql.Driver", s"jdbc:postgresql://localhost/$db", "dev", "dev"
      )
    }

    def dropDatabase(db: String, xa: Transactor[IO])(implicit C: ContextShift[IO]) = IO {
      val conn = DriverManager.getConnection("jdbc:postgresql://localhost/postgres", "dev", "dev");
      val statement = conn.createStatement();
      statement.executeUpdate(s"DROP DATABASE $db");
      conn.close()
    }

    val dbms = Dbms.Postgres
  }

  object MariaDB extends DB[IO] {
    Class.forName("org.mariadb.jdbc.Driver")

    def tx(db: String)(implicit C: ContextShift[IO]): Transactor[IO] = {
      val conn = DriverManager.getConnection("jdbc:mariadb://192.168.1.172/mysql", "dev", "dev");
      val statement = conn.createStatement();
      statement.executeUpdate(s"CREATE DATABASE $db");
      conn.close()

      Transactor.fromDriverManager[IO](
        "org.mariadb.jdbc.Driver", s"jdbc:mariadb://192.168.1.172/$db", "dev", "dev"
      )
    }

    def dropDatabase(db: String, xa: Transactor[IO])(implicit C: ContextShift[IO]) = IO {
      val conn = DriverManager.getConnection("jdbc:mariadb://192.168.1.172/mysql", "dev", "dev");
      val statement = conn.createStatement();
      statement.executeUpdate(s"DROP DATABASE $db");
      conn.close()
    }

    val dbms = Dbms.MariaDb
  }
}
