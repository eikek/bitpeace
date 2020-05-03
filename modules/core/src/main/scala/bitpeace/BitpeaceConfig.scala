package bitpeace

import java.util.UUID
import cats.effect.Sync

case class BitpeaceConfig[F[_]](
    metaTable: String,
    chunkTable: String,
    mimetypeDetect: MimetypeDetect,
    randomIdGen: F[String]
)

object BitpeaceConfig {

  def default[F[_]](implicit F: Sync[F]) =
    BitpeaceConfig(
      "FileMeta",
      "FileChunk",
      MimetypeDetect.none,
      F.delay(UUID.randomUUID.toString)
    )

  def defaultTika[F[_]](implicit F: Sync[F]) =
    default.copy(mimetypeDetect = TikaMimetypeDetect)
}
