package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.util.UpdateSearchLinks
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel._
import com.azavea.stac4s._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SearchService[F[_]: Concurrent](
    apiConfig: ApiConfig,
    xa: Transactor[F]
)(
    implicit contextShift: ContextShift[F],
    timerF: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  implicit val MySpecialPrinter = Printer(true, "")

  val searchEndpoints = new SearchEndpoints[F](apiConfig)
  val defaultLimit    = apiConfig.defaultLimit

  def search(params: SearchParameters): F[Either[Unit, StacSearchCollection]] = {
    val limit         = params.limit getOrElse defaultLimit
    val updatedParams = params.copy(limit = Some(limit))
    for {
      searchResults <- PGStacQueries.search(updatedParams).attempt.transact(xa)
    } yield {
      searchResults.leftMap(_ => ())
    }
  }

  val searchRouteGet =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)({
      case searchParameters =>
        search(searchParameters)
          .map(_.map(UpdateSearchLinks(searchParameters, apiConfig).GET))
    })

  val searchRoutePost =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)({
      case searchParameters =>
        search(searchParameters)
          .map(_.map(UpdateSearchLinks(searchParameters, apiConfig).POST))
    })

  val routes: HttpRoutes[F] =
    searchRouteGet <+> searchRoutePost
}
