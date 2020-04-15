package com.azavea.franklin.api.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.datamodel.CollectionsResponse
import com.azavea.franklin.error.{NotFound => NF}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CollectionsService[F[_]: Sync](xa: Transactor[F])(implicit contextShift: ContextShift[F])
    extends Http4sDsl[F] {

  def listCollections(): F[Either[Unit, Json]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
    } yield {
      Either.right(CollectionsResponse(collections).asJson)
    }

  }

  def getCollectionUnique(rawCollectionId: String): F[Either[NF, Json]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
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

  val routes: HttpRoutes[F] =
    CollectionEndpoints.collectionsList.toRoutes(_ => listCollections()) <+> CollectionEndpoints.collectionUnique
      .toRoutes {
        case collectionId => getCollectionUnique(collectionId)
      }
}
