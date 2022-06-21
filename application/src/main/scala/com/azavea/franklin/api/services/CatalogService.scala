package com.azavea.franklin.api.services

import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel.Catalog
import com.azavea.franklin.error.{NotFound => NF}

import cats.data.EitherT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import monocle.syntax.all._
import org.http4s.dsl.Http4sDsl
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting.Standard
import sttp.tapir.server.http4s._

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.UUID
import com.azavea.franklin.api.endpoints.CatalogEndpoints

class CatalogService[F[_]: Concurrent](
    apiConfig: ApiConfig
)(
    implicit async: Async[F],
    contextShift: ContextShift[F],
    timer: Timer[F],
    serverOptions: Http4sServerOptions[F],
    logger: Logger[F]
) extends Http4sDsl[F] {

  val apiHost               = apiConfig.apiHost
  val stacHierarchy         = apiConfig.stacHierarchy
  val enableTransactions    = apiConfig.enableTransactions

  def getCatalogUnique(raw_catalog_path: List[String]): F[Either[NF, Catalog]] = {
    val catalog_path = raw_catalog_path
      .map(URLDecoder.decode(_, StandardCharsets.UTF_8.toString))
    
    async.pure(Either.fromOption({
        stacHierarchy.findCatalog(catalog_path)
        ???
      },
      NF(s"No catalog found at path ${catalog_path.mkString("/")}")
    ))
  }

  val catalogEndpoints =
    new CatalogEndpoints[F](apiConfig.path)

  val routesList = List(
    Http4sServerInterpreter.toRoutes(catalogEndpoints.catalogUnique)({
      case raw_catalog_path => getCatalogUnique(raw_catalog_path)
    })
  )

  val routes = routesList.foldK

}
