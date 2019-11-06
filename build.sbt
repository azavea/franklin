cancelable in Global := true

lazy val commonDependencies =
  Seq(Dependencies.specs2Core, Dependencies.logbackClassic)

lazy val commonSettings = Seq(
  organization := "com.azavea",
  name := "franklin",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.10",
  scalafmtOnCompile := true,
  scapegoatVersion in ThisBuild := Versions.ScapegoatVersion,
  scalacOptions := Seq(
    "-Ypartial-unification",
    "-language:higherKinds",
    "-deprecation",
    "-feature",
    // Required by ScalaFix
    "-Yrangepos",
    "-Ywarn-unused",
    "-Ywarn-unused-import"
  ),
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
  addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
  addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4"),
  addCompilerPlugin(
    "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
  ),
  addCompilerPlugin(scalafixSemanticdb)
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(api, database, datamodel)
lazy val rootRef = LocalProject("root")

///////////////
// Datamodel //
///////////////
lazy val datamodelSettings = commonSettings ++ Seq(
  name := "datamodel",
  fork in run := true
)

lazy val datamodelDependencies = commonDependencies ++ Seq(
  Dependencies.circeCore,
  Dependencies.emoji,
  Dependencies.tapir,
  Dependencies.circeGeneric,
  Dependencies.circeRefined,
  Dependencies.http4s,
  Dependencies.http4sCirce,
  Dependencies.geotrellisServer,
  Dependencies.refined,
  Dependencies.refinedCats
)

lazy val datamodel = (project in file("datamodel"))
  .settings(datamodelSettings: _*)
  .settings({ libraryDependencies ++= datamodelDependencies })

//////////////
// Database //
//////////////
lazy val databaseSettings = commonSettings ++ Seq(
  name := "database",
  fork in run := true
)

lazy val databaseDependencies = commonDependencies ++ Seq(
  Dependencies.doobie,
  Dependencies.doobieHikari,
  Dependencies.doobiePostgres,
  Dependencies.doobiePostgresCirce,
  Dependencies.doobieSpecs2,
  Dependencies.doobieScalatest,
  Dependencies.doobieRefined,
  Dependencies.flyway,
  Dependencies.postgis
)

lazy val database = (project in file("database"))
  .dependsOn(datamodel)
  .settings(databaseSettings: _*)
  .settings({
    libraryDependencies ++= databaseDependencies
  })

///////////////
//    API    //
///////////////
lazy val apiSettings = commonSettings ++ Seq(
  name := "api",
  fork in run := true,
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

lazy val apiDependencies = commonDependencies ++ databaseDependencies ++ Seq(
  Dependencies.decline,
  Dependencies.http4s,
  Dependencies.http4sCirce,
  Dependencies.http4sDsl,
  Dependencies.http4sServer,
  Dependencies.tapir,
  Dependencies.log4cats,
  Dependencies.pureConfig,
  Dependencies.tapirCirce,
  Dependencies.tapirHttp4sServer,
  Dependencies.tapirOpenAPICirceYAML,
  Dependencies.tapirOpenAPIDocs,
  Dependencies.tapirSwaggerUIHttp4s
)

lazy val api = (project in file("api"))
  .dependsOn(datamodel, database, crawler)
  .settings(apiSettings: _*)
  .settings({
    libraryDependencies ++= apiDependencies
  })

//////////
// DOCS //
//////////
lazy val docs = (project in file("api-docs"))
  .dependsOn(api)
  .enablePlugins(MdocPlugin)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(
    mdocVariables := Map(
      "VERSION" -> version.value
    )
  )

/////////////
// CRAWLER //
/////////////

lazy val crawlerDependencies = Seq(
  Dependencies.circeFs2
)

lazy val crawler = project
  .dependsOn(datamodel, database)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= crawlerDependencies
  )
