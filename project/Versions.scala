// Versions
object Versions {
  val AsyncHttpClientVersion = "2.12.3"
  val AWSVersion             = "1.11.751"
// There were issues with accessing S3 from the Fargate ECS instance
// The easiest fix for now is to rollback dep to the last and the most tested version,
// which is the same as that in the GeoTrellis
// https://github.com/locationtech/geotrellis/blob/v3.6.0/project/Dependencies.scala#L86
  val AWSSdk2Version         = "2.16.13"
  val CatsEffectVersion      = "2.5.5"
  val CatsScalacheckVersion  = "0.3.1"
  val CatsVersion            = "2.8.0"
  val CirceFs2Version        = "0.14.2"
  val CirceJsonSchemaVersion = "0.2.0"
  val CirceVersion           = "0.14.2"
  val DeclineVersion         = "2.3.0"
  val DisciplineScalatest    = "2.2.0"
  val DoobieVersion          = "0.13.4"
  val EmojiVersion           = "1.3.0"
  val Flyway                 = "9.1.4"
  val Fs2Version             = "3.2.12"
  val GeoTrellisVersion      = "3.6.0"
  val GuavaVersion           = "31.1-jre"
  val HikariVersion          = "4.0.3"
  val Http4sVersion          = "0.21.33"
  val JtsVersion             = "1.16.1"
  val LogbackVersion         = "1.2.5"
  val Log4CatsVersion        = "1.1.1"
  val MagnoliaVersion        = "0.17.0"
  val MonocleVersion         = "2.1.0"
  val Postgis                = "2.5.1"
  val PureConfig             = "0.12.1"
  val Refined                = "0.10.1"
  val ScalacacheVersion      = "0.28.0"
  val ScalacheckVersion      = "1.16.0"
  val ScapegoatVersion       = "1.4.11"
  val ShapelessVersion       = "2.3.9"
  val Slf4jVersion           = "1.7.36"
  val Specs2Version          = "4.16.1"
  val Stac4SVersion          = "0.6.2"
  val SttpClientVersion      = "2.3.0"
  val SttpShared             = "1.3.7"
  val SttpModelVersion       = "1.5.0"
  val TapirVersion           = "0.17.20"
  val TapirOpenAPIVersion    = "0.17.20"
  val ThreeTenExtra          = "1.7.1"
  val TypenameVersion        = "1.0.0"
}
