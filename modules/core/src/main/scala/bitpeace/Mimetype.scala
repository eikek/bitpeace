package bitpeace

import javax.activation.{MimeType => JMimeType}

import bitpeace.Compat._
import cats.data.Validated

/** Utility around `javax.activation.Mimetype'. */
case class Mimetype(
    primary: String,
    sub: String,
    params: Map[String, String] = Map.empty
) {

  /** Return {{{other}}} if this is an unknown mimetype. */
  def orElse(other: Mimetype): Mimetype =
    if (this == Mimetype.unknown) other else this

  /** Return basetype as string in {{{primary/sub}}} format. */
  def baseType = s"${primary}/${sub}"

  /** Return the value of parameter {{{name}}}. */
  def param(name: String): Option[String] =
    params.get(name.toLowerCase)

  /** Set a new parameter */
  def param(name: String, value: String): Mimetype =
    copy(params = params.updated(name, value))

  /** Return {{{true}}} if base type of {{{this}}} and {{{other}}} are
    * equal. IOW compare without parameters.
    */
  def baseEqual(other: Mimetype): Boolean =
    baseType == other.baseType

  def matches(other: Mimetype): Boolean =
    primary == other.primary &&
      (sub == other.sub || sub == "*")

  /** Renders this mimetype into its string representation. */
  def asString =
    params.foldLeft(baseType) { case (s, (name, value)) =>
      s + s"""; $name="$value""""
    }
}

object Mimetype {
  val `application/octet-stream` = Mimetype("application", "octet-stream")
  val unknown                    = `application/octet-stream`
  val `application/pdf`          = Mimetype("application", "pdf")
  val `text/html`                = Mimetype("text", "html")
  val `application/x-xz`         = Mimetype("application", "x-xz")
  val `application/zip`          = Mimetype("application", "zip")

  def application(sub: String): Mimetype =
    Mimetype("application", sub)

  def text(sub: String): Mimetype =
    Mimetype("text", sub)

  def apply(primary: String, subtype: String): Mimetype =
    normalize(new JMimeType(primary, subtype).asScala)

  def parse(mt: String): Validated[Throwable, Mimetype] =
    Validated.catchNonFatal(new JMimeType(mt)).map(_.asScala).map(normalize)

  def fromJava(jmt: JMimeType): Mimetype = {
    val paramNames = jmt.getParameters.getNames.asScalaList.map(_.toString)
    val params = paramNames.foldLeft(Map.empty[String, String]) { (map, name) =>
      map.updated(name.toLowerCase, jmt.getParameter(name))
    }
    Mimetype(jmt.getPrimaryType, jmt.getSubType, params)
  }

  def normalize(mt: Mimetype): Mimetype =
    if (!mt.baseType.contains("unknown")) mt
    else unknown

  object BaseType {
    def unapply(mt: Mimetype): Option[(String, String)] =
      Some(mt.primary -> mt.sub)
  }

  implicit class MimetypeOps(mt: JMimeType) {
    def asScala: Mimetype = Mimetype.fromJava(mt)
  }
}
