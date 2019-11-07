package com.azavea.franklin.api

import java.util.concurrent.Executors

import com.lightbend.emoji.ShortCodes.Implicits._
import com.lightbend.emoji.ShortCodes.Defaults._
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
import com.azavea.franklin.endpoints.{
  CollectionItemEndpoints,
  LandingPageEndpoints,
  SearchEndpoints
}
import com.azavea.franklin.services.{
  CollectionItemsService,
  CollectionsService,
  LandingPageService,
  SearchService
}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.monovore.decline._
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Server extends IOApp {

  val rasterIO: ContextShift[IO] = IO.contextShift(
    ExecutionContext.fromExecutor(
      Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("frankil-api-%d").build()
      )
    )
  )

  override implicit val contextShift: ContextShift[IO] = rasterIO

  val banner =
    """
       
   $$$$$$$$$
$$$$$$$$$$$$   ________                            __        __  __           
$$$$$$$$$$$$  /        |                          /  |      /  |/  |          
$$            $$$$$$$$/______   ______   _______  $$ |   __ $$ |$$/  _______  
 $$$$$$$$     $$ |__  /      \ /      \ /       \ $$ |  /  |$$ |/  |/       \ 
$$$$$$$$      $$    |/$$$$$$  |$$$$$$  |$$$$$$$  |$$ |_/$$/ $$ |$$ |$$$$$$$  |
$$$$$$$       $$$$$/ $$ |  $$/ /    $$ |$$ |  $$ |$$   $$<  $$ |$$ |$$ |  $$ |
$             $$ |   $$ |     /$$$$$$$ |$$ |  $$ |$$$$$$  \ $$ |$$ |$$ |  $$ |
$$$$$$        $$ |   $$ |     $$    $$ |$$ |  $$ |$$ | $$  |$$ |$$ |$$ |  $$ |
$$$$$         $$/    $$/       $$$$$$$/ $$/   $$/ $$/   $$/ $$/ $$/ $$/   $$/ 
$$$$
         
""".split("\n").toList

  def createServer(port: Int): Resource[IO, HTTP4sServer[IO]] =
    for {
      connectionEc  <- ExecutionContexts.fixedThreadPool[IO](2)
      transactionEc <- ExecutionContexts.cachedThreadPool[IO]

      xa <- {
        HikariTransactor
          .fromHikariConfig[IO](
            DatabaseConfig.hikariConfig,
            connectionEc,
            transactionEc
          )
      }
      allEndpoints      = LandingPageEndpoints.endpoints ++ CollectionItemEndpoints.endpoints ++ SearchEndpoints.endpoints
      docs              = allEndpoints.toOpenAPI("Franklin", "0.0.1")
      docRoutes         = new SwaggerHttp4s(docs.toYaml, "open-api", "spec.yaml").routes
      landingPageRoutes = new LandingPageService[IO].routes
      searchRoutes      = new SearchService[IO](xa).routes
      collectionRoutes = new CollectionsService[IO](xa).routes <+> new CollectionItemsService[IO](
        xa
      ).routes
      router = CORS(
        Router(
          "/" -> (landingPageRoutes <+> collectionRoutes <+> searchRoutes <+> docRoutes)
        )
      ).orNotFound
      server <- {
        BlazeServerBuilder[IO]
          .bindHttp(port, "0.0.0.0")
          .withBanner(banner)
          .withHttpApp(router)
          .resource
      }
    } yield {
      server
    }

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

  val applicationCommand: Command[Product] =
    Command("", "Your Friendly Neighborhood OGC API - Features and STAC Web Service") {
      runServerOpts orElse runMigrationsOpts
    }

  override def run(args: List[String]): IO[ExitCode] = {
    applicationCommand.parse(args) map {
      case RunServer(port) => createServer(port).use(_ => IO.never).as(ExitCode.Success)
      case RunMigrations() => runMigrations
    } match {
      case Left(e) =>
        IO {
          println(e.toString())
          ExitCode.Error
        }
      case Right(s) => s
    }
  }
}
