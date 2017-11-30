package bitpeace.sql

import doobie.imports._

trait Dbms {

  def blobType: Fragment

}

object Dbms {
  val H2 = new Dbms {
    val blobType = Fragment.const("blob")
  }

  val Postgres = new Dbms {
    val blobType = Fragment.const("bytea")
  }
}
