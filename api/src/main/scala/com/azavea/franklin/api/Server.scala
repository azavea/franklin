package com.azavea.franklin.api

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
import com.azavea.franklin.api.commands.{ApiConfig, Commands, DatabaseConfig}
import com.azavea.franklin.endpoints.{
  CollectionEndpoints,
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
import cats.implicits._

object Server extends IOApp {

  private val banner: List[String] =
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

  private def createServer(
      apiConfig: ApiConfig,
      dbConfig: DatabaseConfig
  ): Resource[IO, HTTP4sServer[IO]] =
    for {
      connectionEc  <- ExecutionContexts.fixedThreadPool[IO](2)
      transactionEc <- ExecutionContexts.cachedThreadPool[IO]
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        dbConfig.jdbcUrl,
        dbConfig.dbUser,
        dbConfig.dbPass,
        connectionEc,
        transactionEc
      )
      allEndpoints      = LandingPageEndpoints.endpoints ++ CollectionEndpoints.endpoints ++ CollectionItemEndpoints.endpoints ++ SearchEndpoints.endpoints
      docs              = allEndpoints.toOpenAPI("Franklin", "0.0.1")
      docRoutes         = new SwaggerHttp4s(docs.toYaml, "open-api", "spec.yaml").routes[IO]
      landingPageRoutes = new LandingPageService[IO].routes
      searchRoutes      = new SearchService[IO](apiConfig, xa).routes
      collectionRoutes = new CollectionsService[IO](xa).routes <+> new CollectionItemsService[IO](
        xa
      ).routes
      router = CORS(
        Router(
          "/" -> ResponseLogger.httpRoutes(false, false)(
            landingPageRoutes <+> collectionRoutes <+> searchRoutes <+> docRoutes
          )
        )
      ).orNotFound
      server <- {
        BlazeServerBuilder[IO]
          .bindHttp(apiConfig.internalPort.value, "0.0.0.0")
          .withBanner(banner)
          .withHttpApp(router)
          .resource
      }
    } yield {
      server
    }

  override def run(args: List[String]): IO[ExitCode] = {
    import Commands._

    applicationCommand.parse(args) map {
      case RunServer(apiConfig, dbConfig) =>
        createServer(apiConfig, dbConfig)
          .use(_ => IO.never)
          .as(ExitCode.Success)
      case RunMigrations(config) => runMigrations(config)
      case RunImport(catalogRoot, dbConfig) =>
        runImport(catalogRoot, dbConfig).compile.drain map { _ =>
          ExitCode.Success
        }
    } match {
      case Left(e) =>
        IO {
          println(e.toString())
        } map { _ =>
          ExitCode.Error
        }
      case Right(s) => s
    }
  }
}
