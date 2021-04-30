package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.{SearchFilters, StacItemDao}
import com.azavea.franklin.datamodel.{Context, SearchMethod, StacSearchCollection}
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._
import com.azavea.franklin.api.commands.ApiConfig

class SearchService[F[_]: Concurrent](
    apiConfig: ApiConfig,
    defaultLimit: NonNegInt,
    enableTiles: Boolean,
    xa: Transactor[F]
)(
    implicit contextShift: ContextShift[F],
    timerF: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  val searchEndpoints = new SearchEndpoints[F]

  def search(searchFilters: SearchFilters, searchMethod: SearchMethod): F[Either[Unit, Json]] = {
    val limit = searchFilters.limit getOrElse defaultLimit
    for {
      itemsFib <- Concurrent[F].start {
        (StacItemDao.query
          .filter(searchFilters)
          .list(limit.value) flatMap { items =>
          StacItemDao.getSearchLinks(items, limit, searchFilters, apiConfig.apiHost, searchMethod) map {
            (items, _)
          }
        }).transact(xa)
      }
      countFib <- Concurrent[F].start {
        StacItemDao.getSearchContext(searchFilters).transact(xa)
      }
      ((items, links), count) <- (itemsFib, countFib).tupled.join
    } yield {
      val withApiHost  = items map { _.updateLinksWithHost(apiConfig) }
      val searchResult = StacSearchCollection(Context(items.length, count), withApiHost, links)
      val updatedFeatures = searchResult.features.map { item =>
        (item.collection, enableTiles) match {
          case (Some(collectionId), true) =>
            item.addTilesLink(apiConfig.apiHost, collectionId, item.id)
          case _ => item
        }
      }
      Either.right(searchResult.copy(features = updatedFeatures).asJson)
    }
  }

  val routes: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)(searchFilters =>
      search(searchFilters, SearchMethod.Get)
    ) <+> Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)({
      case searchFilters => search(searchFilters, SearchMethod.Post)
    })
}
