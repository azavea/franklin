package com.azavea.franklin.api

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.commands.{ApiConfig, Commands, DatabaseConfig}
import com.azavea.franklin.api.endpoints.LandingPageEndpoints
import com.azavea.franklin.api.endpoints.{
  CollectionEndpoints,
  CollectionItemEndpoints,
  SearchEndpoints,
  TileEndpoints
}
import com.azavea.franklin.api.services._
import com.azavea.franklin.extensions.validation.{collectionExtensionsRef, itemExtensionsRef}
import com.azavea.stac4s.{`application/json`, StacLink, StacLinkType}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server.{Router, Server => HTTP4sServer}
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.swagger.http4s.SwaggerHttp4s

import scala.concurrent.ExecutionContext

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Server extends IOApp.WithContext {

  def executionContextResource: Resource[SyncIO, ExecutionContext] =
    Resource
      .make(
        SyncIO(
          Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("raster-io-%d").build()
          )
        )
      )(pool =>
        SyncIO {
          pool.shutdown()
          val _ = pool.awaitTermination(10, TimeUnit.SECONDS)
        }
      )
      .map(ExecutionContext.fromExecutor _)

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

  implicit val serverOptions = ServerOptions.defaultServerOptions[IO]

  private def createServer(
      apiConfig: ApiConfig,
      dbConfig: DatabaseConfig
  ) = {
    val rootLink = StacLink(
      apiConfig.apiHost,
      StacLinkType.StacRoot,
      Some(`application/json`),
      Some("Welcome to Franklin")
    )
    implicit val logger = Slf4jLogger.getLogger[IO]
    AsyncHttpClientCatsBackend.resource[IO]() flatMap { implicit backend =>
      for {
        connectionEc  <- ExecutionContexts.fixedThreadPool[IO](2)
        transactionEc <- ExecutionContexts.cachedThreadPool[IO]
        xa <- HikariTransactor.newHikariTransactor[IO](
          "org.postgresql.Driver",
          dbConfig.jdbcUrl,
          dbConfig.dbUser,
          dbConfig.dbPass,
          connectionEc,
          Blocker.liftExecutionContext(transactionEc)
        )
        collectionItemEndpoints = new CollectionItemEndpoints[IO](
          apiConfig.defaultLimit,
          apiConfig.enableTransactions,
          apiConfig.enableTiles,
          apiConfig.path
        )
        collectionEndpoints = new CollectionEndpoints[IO](
          apiConfig.enableTransactions,
          apiConfig.enableTiles,
          apiConfig.path
        )
        landingPage = new LandingPageEndpoints[IO](apiConfig.path)
        allEndpoints = collectionEndpoints.endpoints ++ collectionItemEndpoints.endpoints ++ new SearchEndpoints[
          IO
        ](apiConfig.path).endpoints ++ new TileEndpoints[
          IO
        ](
          apiConfig.enableTiles,
          apiConfig.path
        ).endpoints ++ landingPage.endpoints
        docs      = OpenAPIDocsInterpreter.toOpenAPI(allEndpoints, "Franklin", "0.0.1")
        docRoutes = new SwaggerHttp4s(docs.toYaml, "open-api", "spec.yaml").routes[IO]
        searchRoutes = new SearchService[IO](
          apiConfig,
          apiConfig.defaultLimit,
          apiConfig.enableTiles,
          xa,
          rootLink
        ).routes
        tileRoutes = new TileService[IO](
          apiConfig.apiHost,
          apiConfig.enableTiles,
          apiConfig.path,
          xa
        ).routes
        itemExtensions       <- Resource.eval { itemExtensionsRef[IO] }
        collectionExtensions <- Resource.eval { collectionExtensionsRef[IO] }
        collectionRoutes = new CollectionsService[IO](xa, apiConfig, collectionExtensions).routes <+> new CollectionItemsService[
          IO
        ](
          xa,
          apiConfig,
          itemExtensions,
          rootLink
        ).routes
        landingPageRoutes = new LandingPageService[IO](apiConfig).routes
        router = CORS(
          ResponseLogger.httpRoutes(false, false)(
            collectionRoutes <+> searchRoutes <+> tileRoutes <+> landingPageRoutes <+> docRoutes
          )
        ).orNotFound
        serverBuilderBlocker <- Blocker[IO]
        server <- {
          BlazeServerBuilder[IO](serverBuilderBlocker.blockingContext)
            .bindHttp(apiConfig.internalPort.value, "0.0.0.0")
            .withConnectorPoolSize(128)
            .withBanner(banner)
            .withHttpApp(router)
            .resource
        }
      } yield {
        server
      }
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    import Commands._

    applicationCommand.parse(args, env = sys.env) map {
      case RunServer(apiConfig, dbConfig) if !apiConfig.runMigrations =>
        createServer(apiConfig, dbConfig)
          .use(_ => IO.never)
          .as(ExitCode.Success)
      case RunServer(apiConfig, dbConfig) =>
        runMigrations(dbConfig) *>
          createServer(apiConfig, dbConfig).use(_ => IO.never).as(ExitCode.Success)
      case RunMigrations(config) => runMigrations(config)
      case RunCatalogImport(catalogRoot, dbConfig, dryRun) =>
        AsyncHttpClientCatsBackend.resource[IO]() use { implicit backend =>
          runCatalogImport(catalogRoot, dbConfig, dryRun) map { _ => ExitCode.Success }
        }
      case RunItemsImport(collectionId, itemUris, dbConfig, dryRun) => {
        AsyncHttpClientCatsBackend.resource[IO]() use { implicit backend =>
          runStacItemImport(collectionId, itemUris, dbConfig, dryRun) map {
            case Left(error) => {
              println(s"Import failed: $error")
              ExitCode.Error
            }
            case Right(numItemsImported) => {
              println(s"Import succesful: ${numItemsImported} items imported")
              ExitCode.Success
            }
          }
        }
      }
    } match {
      case Left(e) =>
        IO {
          println(e.toString())
        } map { _ => ExitCode.Error }
      case Right(s) => s
    }
  }
}
