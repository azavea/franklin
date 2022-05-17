package com.azavea.franklin.api

import com.azavea.franklin.api.endpoints.{
  CollectionEndpoints,
  CollectionItemEndpoints,
  LandingPageEndpoints,
  SearchEndpoints
}
import com.azavea.franklin.api.middleware.AccessLoggingMiddleware
import com.azavea.franklin.api.services._
import com.azavea.franklin.extensions.validation.{collectionExtensionsRef, itemExtensionsRef}
import com.azavea.franklin.commands._

import cats.effect._
import cats.syntax.all._
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
import org.http4s.implicits.{http4sLiteralsSyntax => _, _}
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server.{Router, Server => HTTP4sServer}
import sttp.client.SttpBackend
import sttp.client._
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

  // https://gist.github.com/dirkgr/20a00d30522c1381f0e2
  private def classUrls(cl: ClassLoader): Array[java.net.URL] = cl match {
    case null                       => Array()
    case u: java.net.URLClassLoader => u.getURLs() ++ classUrls(cl.getParent)
    case _                          => classUrls(cl.getParent)
  }

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
        xa <- HikariTransactor.fromHikariConfig[IO](
          dbConfig.toHikariConfig,
          connectionEc,
          Blocker.liftExecutionContext(transactionEc)
        )
        collectionItemEndpoints = new CollectionItemEndpoints[IO](
          apiConfig.defaultLimit,
          apiConfig.enableTransactions,
          apiConfig.path
        )
        collectionEndpoints = new CollectionEndpoints[IO](
          apiConfig.enableTransactions,
          apiConfig.path
        )
        landingPage = new LandingPageEndpoints[IO](apiConfig.path)
        allEndpoints =
          collectionEndpoints.endpoints ++
          collectionItemEndpoints.endpoints ++
          new SearchEndpoints[IO](apiConfig).endpoints ++
          landingPage.endpoints
        docs      = OpenAPIDocsInterpreter.toOpenAPI(allEndpoints, "Franklin", "0.0.1")
        docRoutes = new SwaggerHttp4s(docs.toYaml, "open-api", "spec.yaml").routes[IO]
        searchRoutes = new SearchService[IO](apiConfig, xa, rootLink).routes
        itemExtensions       <- Resource.eval { itemExtensionsRef[IO] }
        collectionExtensions <- Resource.eval { collectionExtensionsRef[IO] }
        collectionRoutes =
          new CollectionsService[IO](xa, apiConfig, collectionExtensions).routes <+>
          new CollectionItemsService[IO](xa, apiConfig, itemExtensions, rootLink).routes
        landingPageRoutes = new LandingPageService[IO](apiConfig).routes
        router = CORS(
          new AccessLoggingMiddleware(
            collectionRoutes <+> searchRoutes <+> landingPageRoutes <+> docRoutes,
            logger
          ).withLogging(true)
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
      case RunServer(apiConfig, dbConfig) =>
        println(s"apiconfig $apiConfig")
        println(s"dbConfig $dbConfig")
        createServer(apiConfig, dbConfig)
          .use(_ => IO.never)
          .as(ExitCode.Success)
    } match {
      case Left(e) =>
        IO {
          println(e.toString())
        } map { _ => ExitCode.Error }
      case Right(s) => s
    }
  }
}
