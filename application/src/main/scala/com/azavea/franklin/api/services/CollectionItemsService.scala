package com.azavea.franklin.api.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.endpoints.CollectionItemEndpoints
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.database.Filterables._
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import io.circe._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._
import eu.timepit.refined.auto._
import org.http4s.HttpRoutes

class CollectionItemsService[F[_]: Sync](xa: Transactor[F])(implicit contextShift: ContextShift[F])
    extends Http4sDsl[F] {

  def collectionFilter: String => Fragment =
    (collectionId: String) => fr"""item @> {"collection":"$collectionId"} :: jsonb"""

  def listCollectionItems(collectionId: String): F[Either[Unit, Json]] = {
    for {
      items <- StacItemDao.query
        .filter(collectionFilter(collectionId))
        .list
        .transact(xa)
    } yield {
      val response = Json.obj(
        ("type", Json.fromString("FeatureCollection")),
        ("features", items.asJson)
      )
      Either.right(response)
    }

  }

  def getCollectionItemUnique(collectionId: String, itemId: String): F[Either[NF, Json]] = {
    for {
      itemOption <- StacItemDao.query
        .filter(fr"id = $itemId")
        .filter(collectionFilter(collectionId))
        .selectOption
        .transact(xa)
    } yield {
      itemOption match {
        case Some(item) => Either.right(item.asJson)
        case _          => Either.left(NF(s"Item $itemId in collection $collectionId not found"))
      }
    }
  }

  val routes: HttpRoutes[F] = CollectionItemEndpoints.collectionItemsList.toRoutes(
    collectionId => listCollectionItems(collectionId)
  ) <+> CollectionItemEndpoints.collectionItemsUnique.toRoutes {
    case (collectionId, itemId) => getCollectionItemUnique(collectionId, itemId)
  }
}
