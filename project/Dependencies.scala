import scala.util.Properties

import sbt._

// Versions
object Versions {
  val CirceFs2Version  = "0.11.0"
  val CirceVersion     = "0.11.1"
  val DeclineVersion   = "0.6.2"
  val DoobieVersion    = "0.7.1"
  val EmojiVersion     = "1.2.1"
  val Flyway           = "5.2.4"
  val GeotrellisServer = "3.4.0-8-g362e95f-SNAPSHOT"
  val Http4sVersion    = "0.20.10"
  val Log4CatsVersion  = "0.3.0"
  val LogbackVersion   = "1.2.3"
  val Postgis          = "2.2.1"
  val PureConfig       = "0.12.1"
  val Refined          = "0.9.3"
  val ScapegoatVersion = "1.3.8"
  val Specs2Version    = "4.6.0"
  val TapirVersion     = "0.10.1"
}

object Dependencies {
  val circeCore             = "io.circe"               %% "circe-core"               % Versions.CirceVersion
  val circeFs2              = "io.circe"               %% "circe-fs2"                % Versions.CirceFs2Version
  val circeGeneric          = "io.circe"               %% "circe-generic"            % Versions.CirceVersion
  val circeRefined          = "io.circe"               %% "circe-refined"            % Versions.CirceVersion
  val decline               = "com.monovore"           %% "decline"                  % Versions.DeclineVersion
  val declineRefined        = "com.monovore"           %% "decline-refined"          % Versions.DeclineVersion
  val doobie                = "org.tpolecat"           %% "doobie-core"              % Versions.DoobieVersion
  val doobieHikari          = "org.tpolecat"           %% "doobie-hikari"            % Versions.DoobieVersion
  val doobiePostgres        = "org.tpolecat"           %% "doobie-postgres"          % Versions.DoobieVersion
  val doobiePostgresCirce   = "org.tpolecat"           %% "doobie-postgres-circe"    % Versions.DoobieVersion
  val doobieRefined         = "org.tpolecat"           %% "doobie-refined"           % Versions.DoobieVersion
  val doobieScalatest       = "org.tpolecat"           %% "doobie-scalatest"         % Versions.DoobieVersion % "test"
  val emoji                 = "com.lightbend"          %% "emoji"                    % Versions.EmojiVersion
  val doobieSpecs2          = "org.tpolecat"           %% "doobie-specs2"            % Versions.DoobieVersion % "test"
  val flyway                = "org.flywaydb"           % "flyway-core"               % Versions.Flyway
  val geotrellisServer      = "com.azavea.geotrellis"  %% "geotrellis-server-stac"   % Versions.GeotrellisServer
  val http4s                = "org.http4s"             %% "http4s-blaze-server"      % Versions.Http4sVersion
  val http4sCirce           = "org.http4s"             %% "http4s-circe"             % Versions.Http4sVersion
  val http4sDsl             = "org.http4s"             %% "http4s-dsl"               % Versions.Http4sVersion
  val http4sServer          = "org.http4s"             %% "http4s-blaze-server"      % Versions.Http4sVersion
  val log4cats              = "io.chrisdavenport"      %% "log4cats-slf4j"           % Versions.Log4CatsVersion
  val logbackClassic        = "ch.qos.logback"         % "logback-classic"           % Versions.LogbackVersion
  val postgis               = "net.postgis"            % "postgis-jdbc"              % Versions.Postgis
  val pureConfig            = "com.github.pureconfig"  %% "pureconfig"               % Versions.PureConfig
  val refined               = "eu.timepit"             %% "refined"                  % Versions.Refined
  val refinedCats           = "eu.timepit"             %% "refined-cats"             % Versions.Refined
  val specs2Core            = "org.specs2"             %% "specs2-core"              % Versions.Specs2Version % "test"
  val tapir                 = "com.softwaremill.tapir" %% "tapir-core"               % Versions.TapirVersion
  val tapirCirce            = "com.softwaremill.tapir" %% "tapir-json-circe"         % Versions.TapirVersion
  val tapirHttp4sServer     = "com.softwaremill.tapir" %% "tapir-http4s-server"      % Versions.TapirVersion
  val tapirOpenAPICirceYAML = "com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % Versions.TapirVersion
  val tapirOpenAPIDocs      = "com.softwaremill.tapir" %% "tapir-openapi-docs"       % Versions.TapirVersion
  val tapirSwaggerUIHttp4s  = "com.softwaremill.tapir" %% "tapir-swagger-ui-http4s"  % Versions.TapirVersion
}
