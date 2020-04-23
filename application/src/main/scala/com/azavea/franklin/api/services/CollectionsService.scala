package com.azavea.franklin.api.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.datamodel.{CollectionsResponse, TileInfo}
import com.azavea.franklin.error.{NotFound => NF}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CollectionsService[F[_]: Sync](
    xa: Transactor[F],
    apiHost: NonEmptyString,
    enableTiles: Boolean
)(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def listCollections(): F[Either[Unit, Json]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
      updated = collections map { _.maybeAddTilesLink(enableTiles, apiHost) }
    } yield {
      Either.right(CollectionsResponse(updated).asJson)
    }

  }

  def getCollectionUnique(rawCollectionId: String): F[Either[NF, Json]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionOption <- StacCollectionDao
        .getCollectionUnique(collectionId)
        .transact(xa)
    } yield {
      Either.fromOption(
        collectionOption map { _.maybeAddTilesLink(enableTiles, apiHost).asJson },
        NF(s"Collection $collectionId not found")
      )
    }
  }

  def getCollectionTiles(rawCollectionId: String): F[Either[NF, (Json, String)]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionOption <- StacCollectionDao
        .getCollectionUnique(collectionId)
        .transact(xa)
    } yield {
      Either.fromOption(
        collectionOption.map(collection =>
          (
            TileInfo.fromStacCollection(apiHost, collection).asJson,
            collection.##.toString
          )
        ),
        NF(s"Collection $collectionId")
      )
    }
  }

  val collectionEndpoints = new CollectionEndpoints(enableTiles)

  val routesList = List(
    collectionEndpoints.collectionsList.toRoutes(_ => listCollections()),
    collectionEndpoints.collectionUnique
      .toRoutes {
        case collectionId => getCollectionUnique(collectionId)
      }
  ) ++ (if (enableTiles) {
          List(collectionEndpoints.collectionTiles.toRoutes(getCollectionTiles))
        } else Nil)

  val routes = routesList.foldK

}
