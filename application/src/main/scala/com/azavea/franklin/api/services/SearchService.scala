package com.azavea.franklin.api.services

import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.datamodel.{Context, SearchMethod, SearchParameters, StacSearchCollection}
import com.azavea.franklin.database.PGStacQueries

import cats.effect._
import cats.syntax.all._
import com.azavea.stac4s.StacLink
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

class SearchService[F[_]: Concurrent](
    apiConfig: ApiConfig,
    xa: Transactor[F],
    rootLink: StacLink
)(
    implicit contextShift: ContextShift[F],
    timerF: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  val searchEndpoints = new SearchEndpoints[F](apiConfig)
  val defaultLimit    = apiConfig.defaultLimit

  def search(params: SearchParameters): F[Either[Unit, Json]] = {
    val limit = params.limit getOrElse defaultLimit
    for {
      searchResults <- PGStacQueries.search(params).transact(xa)
    } yield {
      searchResults
    }
  }
  // {
  //   for {
  //     itemsFib <- Concurrent[F].start {
  //       (StacItemDao.query
  //         .filter(searchParameters)
  //         .list(limit.value) flatMap { items =>
  //         StacItemDao.getSearchLinks(items, limit, searchParameters, apiConfig.apiHost, searchMethod) map {
  //           (items, _)
  //         }
  //       }).transact(xa)
  //     }
  //     countFib <- Concurrent[F].start {
  //       StacItemDao.getSearchContext(searchParameters).transact(xa)
  //     }
  //     ((items, links), count) <- (itemsFib, countFib).tupled.join
  //   } yield {
  //     val withApiHost = items map { _.updateLinksWithHost(apiConfig) }
  //     val searchResult =
  //       StacSearchCollection(Context(limit, items.length, count), withApiHost, links)
  //     val updatedFeatures = searchResult.features
  //       .map { item =>
  //         ((item.collection, enableTiles) match {
  //           case (Some(collectionId), true) =>
  //             item.addTilesLink(apiConfig.apiHost, collectionId, item.id)
  //           case _ => item
  //         }).addRootLink(rootLink)
  //       }

  //     Either.right(searchResult.copy(features = updatedFeatures).asJson)
  //   }
  // }

  val routes: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)(searchParameters =>
      search(searchParameters)
    ) <+> Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)({
      case searchParameters => search(searchParameters)
    })
}
