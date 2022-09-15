Global / cancelable := true

lazy val commonSettings = Seq(
  organization := "com.azavea",
  name := "franklin",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.15",
  Compile / run / mainClass := Some("com.azavea.franklin.api.Server"),
  ThisBuild / scapegoatVersion := Versions.ScapegoatVersion,
  autoCompilerPlugins := true,
  scalacOptions ~= filterConsoleScalacOptions,
  externalResolvers := Seq(
    DefaultMavenRepository,
    Resolver.sonatypeRepo("snapshots"),
    Resolver.typesafeIvyRepo("releases"),
    // Required transitively
    Resolver.bintrayRepo("azavea", "maven"),
    Resolver.bintrayRepo("azavea", "geotrellis"),
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
    Resolver.bintrayRepo("guizmaii", "maven"),
    Resolver.bintrayRepo("colisweb", "maven"),
    "jitpack".at("https://jitpack.io"),
    Resolver.file("local", file(Path.userHome.absolutePath + "/.ivy2/local"))(
      Resolver.ivyStylePatterns
    )
  ),
  outputStrategy := Some(StdoutOutput),
  scalacOptions ++= Seq(
    "-Ypartial-unification",
    "-Yrangepos"
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin(
    "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
  ),
  unusedCompileDependenciesFilter -= moduleFilter(
    "com.sksamuel.scapegoat",
    "scalac-scapegoat-plugin"
  ),
  unusedCompileDependenciesFilter -= moduleFilter(
    "org.slf4j",
    "slf4j-simple"
  ),
  excludeDependencies ++= Seq(
    "log4j"     % "log4j",
    "org.slf4j" % "slf4j-log4j12",
    "org.slf4j" % "slf4j-nop"
  ),
  addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.5.8" cross CrossVersion.full),
  addCompilerPlugin(scalafixSemanticdb)
)

// Enable a basic import sorter -- rules are defined in .scalafix.conf
ThisBuild / scalafixDependencies +=
  "com.nequissimus" %% "sort-imports" % "0.6.1"

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(application)

///////////////////////
//    Application    //
///////////////////////
lazy val applicationSettings = commonSettings ++ Seq(
  name := "application",
  run / fork := true,
  assembly / test := {},
  assembly / assemblyJarName := "franklin-api-assembly.jar",
  assembly / assemblyMergeStrategy := {
    case "reference.conf"                       => MergeStrategy.concat
    case "application.conf"                     => MergeStrategy.concat
    case n if n.startsWith("META-INF/services") => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") =>
      MergeStrategy.singleOrError
    case _ => MergeStrategy.first
  }
)

lazy val applicationDependencies = Seq(
  "org.scalactic"                %% "scalactic"                      % "3.2.12",
  "org.scalatest"                %% "scalatest"                      % "3.2.12" % "test",
  "software.amazon.awssdk"       % "sdk-core"                        % Versions.AWSSdk2Version,
  "com.amazonaws"                % "aws-java-sdk-core"               % Versions.AWSVersion,
  "com.amazonaws"                % "aws-java-sdk-s3"                 % Versions.AWSVersion,
  "co.fs2"                       %% "fs2-core"                       % Versions.Fs2Version,
  "com.azavea.stac4s"            %% "core"                           % Versions.Stac4SVersion,
  "com.azavea.stac4s"            %% "testing"                        % Versions.Stac4SVersion % Test,
  "com.chuusai"                  %% "shapeless"                      % Versions.ShapelessVersion,
  "com.github.julien-truffaut"   %% "monocle-core"                   % Versions.MonocleVersion,
  "com.github.julien-truffaut"   %% "monocle-macro"                  % Versions.MonocleVersion,
  "com.google.guava"             % "guava"                           % Versions.GuavaVersion,
  "com.lightbend"                %% "emoji"                          % Versions.EmojiVersion,
  "com.lihaoyi"                  %% "os-lib"                         % Versions.OsLib,
  "com.monovore"                 %% "decline-refined"                % Versions.DeclineVersion,
  "com.monovore"                 %% "decline"                        % Versions.DeclineVersion,
  "com.propensive"               %% "magnolia"                       % Versions.MagnoliaVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend"      % Versions.SttpClientVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % Versions.SttpClientVersion,
  "com.softwaremill.sttp.client" %% "circe"                          % Versions.SttpClientVersion,
  "com.softwaremill.sttp.client" %% "core"                           % Versions.SttpClientVersion,
  "com.softwaremill.sttp.client" %% "json-common"                    % Versions.SttpClientVersion,
  "com.softwaremill.sttp.shared" %% "core"                           % Versions.SttpShared,
  "com.softwaremill.sttp.shared" %% "fs2-ce2"                        % Versions.SttpShared,
  "com.softwaremill.sttp.model"  %% "core"                           % Versions.SttpModelVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-core"                     % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-http4s-server"            % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-json-circe"               % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-openapi-circe-yaml"       % Versions.TapirOpenAPIVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-openapi-docs"             % Versions.TapirOpenAPIVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-openapi-model"            % Versions.TapirOpenAPIVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-refined"                  % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir"  %% "tapir-sttp-client"              % Versions.TapirVersion % Test,
  "com.softwaremill.sttp.tapir"  %% "tapir-swagger-ui-http4s"        % Versions.TapirVersion,
  "com.zaxxer"                   % "HikariCP"                        % Versions.HikariVersion,
  "eu.timepit"                   %% "refined-scalacheck"             % Versions.Refined % Test,
  "eu.timepit"                   %% "refined"                        % Versions.Refined,
  "io.chrisdavenport"            %% "cats-scalacheck"                % Versions.CatsScalacheckVersion % Test,
  "io.chrisdavenport"            %% "log4cats-core"                  % Versions.Log4CatsVersion,
  "io.chrisdavenport"            %% "log4cats-noop"                  % Versions.Log4CatsVersion % Test,
  "io.chrisdavenport"            %% "log4cats-slf4j"                 % Versions.Log4CatsVersion,
  "io.circe"                     %% "circe-core"                     % Versions.CirceVersion,
  "io.circe"                     %% "circe-generic"                  % Versions.CirceVersion,
  "io.circe"                     %% "circe-json-schema"              % Versions.CirceJsonSchemaVersion,
  "io.circe"                     %% "circe-parser"                   % Versions.CirceVersion,
  "io.circe"                     %% "circe-refined"                  % Versions.CirceVersion,
  "io.circe"                     %% "circe-testing"                  % Versions.CirceVersion % Test,
  "io.circe"                     %% "circe-optics"                   % Versions.CirceVersion,
  "net.postgis"                  % "postgis-geometry"                % Versions.Postgis,
  "net.postgis"                  % "postgis-jdbc"                    % Versions.Postgis,
  "org.asynchttpclient"          % "async-http-client"               % Versions.AsyncHttpClientVersion,
  "org.flywaydb"                 % "flyway-core"                     % Versions.Flyway,
  "org.http4s"                   %% "http4s-blaze-server"            % Versions.Http4sVersion,
  "org.http4s"                   %% "http4s-blaze-server"            % Versions.Http4sVersion,
  "org.http4s"                   %% "http4s-circe"                   % Versions.Http4sVersion % Test,
  "org.http4s"                   %% "http4s-core"                    % Versions.Http4sVersion,
  "org.http4s"                   %% "http4s-dsl"                     % Versions.Http4sVersion,
  "org.http4s"                   %% "http4s-server"                  % Versions.Http4sVersion,
  "org.locationtech.geotrellis"  %% "geotrellis-layer"               % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis"  %% "geotrellis-proj4"               % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis"  %% "geotrellis-raster"              % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis"  %% "geotrellis-s3"                  % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis"  %% "geotrellis-vector"              % Versions.GeoTrellisVersion,
  "org.locationtech.jts"         % "jts-core"                        % Versions.JtsVersion,
  "org.scalacheck"               %% "scalacheck"                     % Versions.ScalacheckVersion % Test,
  "org.slf4j"                    % "slf4j-api"                       % Versions.Slf4jVersion,
  "org.slf4j"                    % "slf4j-simple"                    % Versions.Slf4jVersion,
  "org.specs2"                   %% "specs2-core"                    % Versions.Specs2Version % Test,
  "org.specs2"                   %% "specs2-core"                    % Versions.Specs2Version % Test,
  "org.specs2"                   %% "specs2-scalacheck"              % Versions.Specs2Version % Test,
  "org.threeten"                 % "threeten-extra"                  % Versions.ThreeTenExtra,
  "org.tpolecat"                 %% "doobie-core"                    % Versions.DoobieVersion,
  "org.tpolecat"                 %% "doobie-free"                    % Versions.DoobieVersion,
  "org.tpolecat"                 %% "doobie-hikari"                  % Versions.DoobieVersion,
  "org.tpolecat"                 %% "doobie-postgres-circe"          % Versions.DoobieVersion,
  "org.tpolecat"                 %% "doobie-postgres"                % Versions.DoobieVersion,
  "org.tpolecat"                 %% "doobie-refined"                 % Versions.DoobieVersion,
  "org.tpolecat"                 %% "doobie-scalatest"               % Versions.DoobieVersion % Test,
  "org.tpolecat"                 %% "doobie-specs2"                  % Versions.DoobieVersion % Test,
  "org.tpolecat"                 %% "typename"                       % Versions.TypenameVersion,
  "org.typelevel"                %% "cats-core"                      % Versions.CatsVersion,
  "org.typelevel"                %% "cats-effect"                    % Versions.CatsEffectVersion,
  "org.typelevel"                %% "cats-free"                      % Versions.CatsVersion,
  "org.typelevel"                %% "cats-kernel"                    % Versions.CatsVersion,
  "org.typelevel"                %% "discipline-scalatest"           % Versions.DisciplineScalatest % Test
)

lazy val application = (project in file("application"))
  .settings(applicationSettings: _*)
  .settings({
    libraryDependencies ++= applicationDependencies
  })

//////////
// DOCS //
//////////
lazy val docs = (project in file("api-docs"))
  .dependsOn(application)
  .enablePlugins(MdocPlugin)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )
