package bitpeace

import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.{HttpHeaders, Metadata, TikaMetadataKeys}
import org.apache.tika.mime.MediaType
import scodec.bits.ByteVector

/** Use Tika for content type detection.
  *
  * Remember to add a dependency to tika, as this is an optional
  * feature.
  */
object TikaMimetypeDetect extends MimetypeDetect {
  private val tika = new TikaConfig().getDetector

  private def convert(mt: MediaType): Mimetype =
    Option(mt)
      .map(_.toString)
      .map(Mimetype.parse)
      .flatMap(_.toOption)
      .map(normalize)
      .getOrElse(Mimetype.unknown)

  private def makeMetadata(hint: MimetypeHint): Metadata = {
    val md = new Metadata
    hint.filename.foreach(md.set(TikaMetadataKeys.RESOURCE_NAME_KEY, _))
    hint.advertised.foreach(md.set(HttpHeaders.CONTENT_TYPE, _))
    md
  }

  private def normalize(in: Mimetype): Mimetype =
    in match {
      case Mimetype(_, sub, p) if sub contains "xhtml" =>
        Mimetype.`text/html`.copy(params = p)
      case _ => in
    }

  def fromBytes(bv: ByteVector, hint: MimetypeHint): Mimetype =
    convert(tika.detect(new java.io.ByteArrayInputStream(bv.toArray), makeMetadata(hint)))

  def fromName(filename: String, advertised: String = ""): Mimetype =
    convert(
      tika.detect(
        null,
        makeMetadata(MimetypeHint.filename(filename).withAdvertised(advertised))
      )
    )
}
