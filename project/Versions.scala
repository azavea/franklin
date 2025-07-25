// Versions
object Versions {
  val AsyncHttpClientVersion = "3.0.1"
  val AWSVersion             = "1.11.751"
// There were issues with accessing S3 from the Fargate ECS instance
// The easiest fix for now is to rollback dep to the last and the most tested version,
// which is the same as that in the GeoTrellis
// https://github.com/locationtech/geotrellis/blob/v3.6.0/project/Dependencies.scala#L86
  val AWSSdk2Version         = "2.30.16"
  val CatsEffectVersion      = "2.5.4"
  val CatsScalacheckVersion  = "0.3.1"
  val CatsVersion            = "2.13.0"
  val CirceFs2Version        = "0.14.1"
  val CirceJsonSchemaVersion = "0.2.0"
  val CirceVersion           = "0.14.1"
  val DeclineVersion         = "2.5.0"
  val DisciplineScalatest    = "2.3.0"
  val DoobieVersion          = "0.13.4"
  val EmojiVersion           = "1.2.3"
  val Fs2Version             = "2.5.12"
  val GeoTrellisVersion      = "3.6.3"
  val GuavaVersion           = "33.4.8-jre"
  val HikariVersion          = "6.3.1"
  val Http4sVersion          = "0.21.34"
  val JtsVersion             = "1.18.1"
  val LogbackVersion         = "1.2.5"
  val Log4CatsVersion        = "1.1.1"
  val MagnoliaVersion        = "0.17.0"
  val MonocleVersion         = "2.1.0"
  val OsLib                  = "0.11.3"
  val Postgis                = "2.5.1"
  val PureConfig             = "0.12.1"
  val Refined                = "0.11.3"
  val ScalacheckVersion      = "1.18.1"
  val ScapegoatVersion       = "1.4.11"
  val ShapelessVersion       = "2.3.12"
  val Slf4jVersion           = "2.0.16"
  val Specs2Version          = "4.20.9"
  val Stac4SVersion          = "0.8.1"
  val SttpClientVersion      = "2.3.0"
  val SttpShared             = "1.4.2"
  val SttpModelVersion       = "1.4.26"
  val TapirVersion           = "0.17.20"
  val TapirOpenAPIVersion    = "0.17.20"
  val ThreeTenExtra          = "1.8.0"
  val TypenameVersion        = "1.1.0"
}
