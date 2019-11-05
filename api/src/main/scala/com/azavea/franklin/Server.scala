package com.azavea.franklin.api

import com.azavea.franklin.api.endpoints.HelloEndpoints
import com.azavea.franklin.api.services.HelloService
import com.azavea.franklin.database.DatabaseConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server.{Router, Server => HTTP4sServer}
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import tapir.swagger.http4s.SwaggerHttp4s
import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import org.flywaydb.core.Flyway

object Server
    extends CommandIOApp(
      name = "",
      header = "Your friendly OGC Features API and STAC Web Service",
      version = "0.0.x"
    ) {

  def createServer(port: Int): Resource[IO, HTTP4sServer[IO]] =
    for {
      connectionEc <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker      <- Blocker[IO]
      _ <- HikariTransactor
        .fromHikariConfig[IO](
          DatabaseConfig.hikariConfig,
          connectionEc,
          blocker
        )
      allEndpoints = HelloEndpoints.endpoints
      docs         = allEndpoints.toOpenAPI("Franklin", "0.0.1")
      docRoutes    = new SwaggerHttp4s(docs.toYaml).routes
      helloRoutes  = new HelloService[IO].routes
      router       = CORS(Router("/api" -> (helloRoutes <+> docRoutes))).orNotFound
      server <- BlazeServerBuilder[IO]
        .bindHttp(port, "0.0.0.0")
        .withHttpApp(router)
        .resource
    } yield server

  case class RunMigrations()

  val runMigrationsOpts: Opts[RunMigrations] =
    Opts.subcommand("migrate", "Runs migrations against database") {
      Opts.unit.map(_ => RunMigrations())
    }

  def runMigrations: IO[ExitCode] = IO {
    Flyway
      .configure()
      .dataSource(
        s"${DatabaseConfig.jdbcUrl}",
        DatabaseConfig.dbUser,
        DatabaseConfig.dbPassword
      )
      .locations("classpath:migrations/")
      .load()
      .migrate()
    ExitCode.Success
  }

  case class RunServer(port: Int)

  val runServerOpts: Opts[RunServer] =
    Opts.subcommand("server", "Runs web service") {
      Opts
        .option[Int]("port", help = "Port to start web service on")
        .withDefault(9090)
        .map(RunServer)
    }

  override def main: Opts[IO[ExitCode]] = (runServerOpts orElse runMigrationsOpts) map {
    case RunServer(port) => createServer(port).use(_ => IO.never).as(ExitCode.Success)
    case RunMigrations() => runMigrations
  }

}
