package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.SearchEndpoints
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

  def search(params: SearchParameters, method: Method): F[Either[Unit, StacSearchCollection]] =
    for {
      searchResults <- PGStacQueries.search(params, method, apiConfig).attempt.transact(xa)
    } yield {
      searchResults.leftMap(_ => ())
    }

  val searchRouteGet =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)(search(_, Method.GET))

  val searchRoutePost =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)(search(_, Method.POST))

  val routes: HttpRoutes[F] =
    searchRouteGet <+> searchRoutePost
}
