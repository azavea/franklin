package com.azavea.franklin.api.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.database.{SearchFilters, StacItemDao}
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

class SearchService[F[_]: Sync](xa: Transactor[F])(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def search(searchFilters: SearchFilters): F[Either[Unit, Json]] = {
    for {
      searchResult <- StacItemDao.getSearchResult(searchFilters).transact(xa)
    } yield {
      Either.right(searchResult.asJson)
    }
  }

  val routes: HttpRoutes[F] =
    SearchEndpoints.searchGet.toRoutes(searchFilters => search(searchFilters)) <+> SearchEndpoints.searchPost
      .toRoutes {
        case searchFilters => search(searchFilters)
      }
}
