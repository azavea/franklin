package com.azavea.franklin.api.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.{SearchFilters, StacCollectionDao, StacItemDao}
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.StacLinkType.{Child, Self}
import com.azavea.stac4s.{`application/json`}
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

class SearchService[F[_]: Sync](apiConfig: ApiConfig, xa: Transactor[F])(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def rootSearch: F[Either[Unit, Json]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
    } yield {
      val collectionLinks = collections.map { collection =>
        val href: NonEmptyString = apiConfig.apiHost + s"/collections/${collection.id}"
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
        apiConfig.apiHost + "/search",
        Self,
        Some(`application/json`),
        Some(NonEmptyString("Franklin STAC API"))
      )

      Either.right(
        APIStacRoot(
          NonEmptyString("rootCatalog"),
          NonEmptyString("The Franklin STAC Root"),
          NonEmptyString(
            "The Root of Franklin's catalog search - all sub collections are linked here"
          ),
          selfLink :: collectionLinks
        ).asJson
      )
    }
  }

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
