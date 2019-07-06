package bitpeace

import scala.jdk.CollectionConverters._

object Compat {

  implicit final class EnumConv[A](jl: java.util.Enumeration[A]) {
    def asScalaList = jl.asScala.toSeq
  }
}
