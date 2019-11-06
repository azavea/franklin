package com.azavea.franklin.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.error.{NotFound => NF}
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.datamodel.CollectionsResponse
import com.azavea.franklin.endpoints.CollectionEndpoints
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._
import eu.timepit.refined.auto._

class CollectionsService[F[_]: Sync](xa: Transactor[F])(implicit contextShift: ContextShift[F])
    extends Http4sDsl[F] {

  def listCollections(): F[Either[Unit, Json]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
    } yield {
      Either.right(CollectionsResponse(collections).asJson)
    }

  }

  def getCollectionUnique(collectionId: String): F[Either[NF, Json]] = {
    for {
      collectionOption <- StacCollectionDao
        .getCollectionUnique(collectionId)
        .transact(xa)
    } yield {
      collectionOption match {
        case Some(collection) => Either.right(collection.asJson)
        case _                => Either.left(NF(s"Collection $collectionId not found"))
      }
    }
  }

  val routes: HttpRoutes[F] = CollectionEndpoints.collectionsList.toRoutes(
    _ => listCollections()
  ) <+> CollectionEndpoints.collectionUnique.toRoutes {
    case collectionId => getCollectionUnique(collectionId)
  }
}
