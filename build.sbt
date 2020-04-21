cancelable in Global := true

lazy val commonSettings = Seq(
  organization := "com.azavea",
  name := "franklin",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.10",
  scapegoatVersion in ThisBuild := Versions.ScapegoatVersion,
  autoCompilerPlugins := true,
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
  scalacOptions += "-Yrangepos",
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full),
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
  addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.3.7" cross CrossVersion.full),
  addCompilerPlugin(scalafixSemanticdb)
)

// Enable a basic import sorter -- rules are defined in .scalafix.conf
scalafixDependencies in ThisBuild +=
  "com.nequissimus" %% "sort-imports" % "0.3.2"

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(application)

///////////////////////
//    Application    //
///////////////////////
lazy val applicationSettings = commonSettings ++ Seq(
  name := "application",
  fork in run := true,
  test in assembly := {},
  assemblyJarName in assembly := "franklin-api-assembly.jar",
  assemblyMergeStrategy in assembly := {
    case "reference.conf"                       => MergeStrategy.concat
    case "application.conf"                     => MergeStrategy.concat
    case n if n.startsWith("META-INF/services") => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") =>
      MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _                      => MergeStrategy.first
  }
)

lazy val applicationDependencies = Seq(
  "co.fs2"                      %% "fs2-core"                 % Versions.Fs2Version,
  "com.amazonaws"               % "aws-java-sdk-core"         % Versions.AWSVersion,
  "com.amazonaws"               % "aws-java-sdk-s3"           % Versions.AWSVersion,
  "com.azavea.geotrellis"       %% "geotrellis-server-core"   % Versions.GeotrellisServerVersion,
  "com.azavea.geotrellis"       %% "maml-jvm"                 % Versions.MamlVersion,
  "com.azavea.stac4s"           %% "core"                     % Versions.Stac4SVersion,
  "com.chuusai"                 %% "shapeless"                % Versions.ShapelessVersion,
  "com.github.cb372"            %% "scalacache-caffeine"      % Versions.ScalacacheVersion,
  "com.github.cb372"            %% "scalacache-core"          % Versions.ScalacacheVersion,
  "com.google.guava"            % "guava"                     % Versions.GuavaVersion,
  "com.lightbend"               %% "emoji"                    % Versions.EmojiVersion,
  "com.lihaoyi"                 %% "sourcecode"               % Versions.SourceCodeVersion,
  "com.lihaoyi"                 %% "sourcecode"               % Versions.SourceCodeVersion,
  "com.monovore"                %% "decline"                  % Versions.DeclineVersion,
  "com.monovore"                %% "decline-refined"          % Versions.DeclineVersion,
  "com.propensive"              %% "magnolia"                 % Versions.MagnoliaVersion,
  "com.propensive"              %% "mercator"                 % Versions.MercatorVersion,
  "com.softwaremill.sttp.model" %% "core"                     % Versions.SttpModelVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-core"               % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"      % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-model"      % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-refined"            % Versions.TapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s"  % Versions.TapirVersion,
  "com.typesafe.scala-logging"  %% "scala-logging"            % Versions.ScalaLoggingVersion,
  "com.zaxxer"                  % "HikariCP"                  % Versions.HikariVersion,
  "eu.timepit"                  %% "refined"                  % Versions.Refined,
  "io.chrisdavenport"           %% "cats-scalacheck"          % Versions.CatsScalacheckVersion % "test",
  "io.chrisdavenport"           %% "log4cats-core"            % Versions.Log4CatsVersion,
  "io.chrisdavenport"           %% "log4cats-slf4j"           % Versions.Log4CatsVersion,
  "io.circe"                    %% "circe-core"               % Versions.CirceVersion,
  "io.circe"                    %% "circe-generic"            % Versions.CirceVersion,
  "io.circe"                    %% "circe-numbers"            % Versions.CirceVersion,
  "io.circe"                    %% "circe-parser"             % Versions.CirceVersion,
  "io.circe"                    %% "circe-refined"            % Versions.CirceVersion,
  "net.postgis"                 % "postgis-jdbc"              % Versions.Postgis,
  "org.flywaydb"                % "flyway-core"               % Versions.Flyway,
  "org.http4s"                  %% "http4s-blaze-server"      % Versions.Http4sVersion,
  "org.http4s"                  %% "http4s-blaze-server"      % Versions.Http4sVersion,
  "org.http4s"                  %% "http4s-circe"             % Versions.Http4sVersion % "test",
  "org.http4s"                  %% "http4s-core"              % Versions.Http4sVersion,
  "org.http4s"                  %% "http4s-dsl"               % Versions.Http4sVersion,
  "org.http4s"                  %% "http4s-server"            % Versions.Http4sVersion,
  "org.locationtech.geotrellis" %% "geotrellis-layer"         % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis" %% "geotrellis-proj4"         % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis" %% "geotrellis-raster"        % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis" %% "geotrellis-s3"            % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis" %% "geotrellis-util"          % Versions.GeoTrellisVersion,
  "org.locationtech.geotrellis" %% "geotrellis-vector"        % Versions.GeoTrellisVersion,
  "org.locationtech.jts"        % "jts-core"                  % Versions.JtsVersion,
  "org.scalacheck"              %% "scalacheck"               % Versions.ScalacheckVersion % "test",
  "org.slf4j"                   % "slf4j-api"                 % Versions.Slf4jVersion,
  "org.slf4j"                   % "slf4j-simple"              % Versions.Slf4jVersion,
  "org.specs2"                  %% "specs2-core"              % Versions.Specs2Version % "test",
  "org.specs2"                  %% "specs2-core"              % Versions.Specs2Version % "test",
  "org.specs2"                  %% "specs2-scalacheck"        % Versions.Specs2Version % "test",
  "org.spire-math"              %% "spire"                    % Versions.SpireVersion,
  "org.tpolecat"                %% "doobie-core"              % Versions.DoobieVersion,
  "org.tpolecat"                %% "doobie-free"              % Versions.DoobieVersion,
  "org.tpolecat"                %% "doobie-hikari"            % Versions.DoobieVersion,
  "org.tpolecat"                %% "doobie-postgres"          % Versions.DoobieVersion,
  "org.tpolecat"                %% "doobie-postgres-circe"    % Versions.DoobieVersion,
  "org.tpolecat"                %% "doobie-scalatest"         % Versions.DoobieVersion % "test",
  "org.tpolecat"                %% "doobie-specs2"            % Versions.DoobieVersion % "test",
  "org.typelevel"               %% "cats-core"                % Versions.CatsVersion,
  "org.typelevel"               %% "cats-effect"              % Versions.CatsEffectVersion,
  "org.typelevel"               %% "cats-free"                % Versions.CatsVersion
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
