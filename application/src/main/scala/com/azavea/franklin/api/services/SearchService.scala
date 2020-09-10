package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.{SearchFilters, StacItemDao}
import com.azavea.franklin.datamodel.SearchMethod
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

class SearchService[F[_]: Sync](
    apiHost: NonEmptyString,
    defaultLimit: NonNegInt,
    enableTiles: Boolean,
    xa: Transactor[F]
)(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def search(searchFilters: SearchFilters, searchMethod: SearchMethod): F[Either[Unit, Json]] = {
    for {
      searchResult <- StacItemDao
        .getSearchResult(
          searchFilters,
          searchFilters.limit getOrElse defaultLimit,
          apiHost,
          searchMethod
        )
        .transact(xa)
    } yield {
      val updatedFeatures = searchResult.features.map { item =>
        (item.collection, enableTiles) match {
          case (Some(collectionId), true) => item.addTilesLink(apiHost.value, collectionId, item.id)
          case _                          => item
        }
      }
      Either.right(searchResult.copy(features = updatedFeatures).asJson)
    }
  }

  val routes: HttpRoutes[F] =
    SearchEndpoints.searchGet.toRoutes(searchFilters => search(searchFilters, SearchMethod.Get)) <+> SearchEndpoints.searchPost
      .toRoutes {
        case searchFilters => search(searchFilters, SearchMethod.Post)
      }
}
