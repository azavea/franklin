package com.azavea.franklin.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.database.{StacCollectionDao, StacItemDao}
import com.azavea.franklin.datamodel._
import com.azavea.franklin.endpoints.SearchEndpoints
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.server.stac.{`application/json`, Child, Data, Self}
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._

class SearchService[F[_]: Sync](apiConfig: ApiConfig, xa: Transactor[F])(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def rootSearch: F[Either[Unit, Json]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
    } yield {
      val collectionLinks = collections.map { collection =>
        val href = NonEmptyString.unsafeFrom(s"${apiConfig.apiHost}/collections/${collection.id}")
        val title = collection.title match {
          case Some(s) if s.length > 0 => Some(NonEmptyString.unsafeFrom(s))
          case _                       => None
        }
        Link(
          href,
          Child,
          Some(`application/json`),
          title
        )
      }

      val selfLink = Link(
        NonEmptyString("http://localhost:9090/stac"),
        Self,
        Some(`application/json`),
        Some(NonEmptyString("Franklin STAC API"))
      )

      val searchLink = Link(
        NonEmptyString("http://localhost:9090/stac/search"),
        Data,
        Some(`application/json`),
        Some(NonEmptyString("Franklin STAC Search API"))
      )

      Either.right(
        APIStacRoot(
          NonEmptyString("rootCatalog"),
          NonEmptyString("The Franklin STAC Root"),
          NonEmptyString(
            "The Root of Franklin's catalog search - all sub collections are linked here"
          ),
          selfLink :: searchLink :: collectionLinks
        ).asJson
      )
    }
  }

  def search: F[Either[Unit, Json]] = {
    StacItemDao
      .getSearchResult(10, 0)
      .transact(xa)
      .map(result => Either.right(result.asJson))
  }

  val routes
      : HttpRoutes[F] = SearchEndpoints.rootCatalog.toRoutes(_ => rootSearch) <+> SearchEndpoints.searchGet
    .toRoutes(_ => search) <+> SearchEndpoints.searchPost.toRoutes(_ => search)
}
