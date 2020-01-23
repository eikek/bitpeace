package bitpeace

case class MimetypeHint(
    filename: Option[String],
    advertised: Option[String]
) {

  def withFilename(name: String)       = copy(filename = Some(name))
  def withAdvertised(mimetype: String) = copy(advertised = Some(mimetype))
}

object MimetypeHint {
  val none = MimetypeHint(None, None)

  def filename(name: String): MimetypeHint =
    MimetypeHint(Some(name), None)

  def advertised(mimetype: String): MimetypeHint =
    MimetypeHint(None, Some(mimetype))
}
