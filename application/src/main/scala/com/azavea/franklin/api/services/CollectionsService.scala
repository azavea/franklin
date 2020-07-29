package com.azavea.franklin.api.services

import cats.effect._
import cats.implicits._
import com.azavea.franklin
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.datamodel.{CollectionsResponse, TileInfo}
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import io.circe._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.azavea.franklin.api.endpoints.AcceptHeader

class CollectionsService[F[_]: Sync](
    xa: Transactor[F],
    apiConfig: ApiConfig
)(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  val apiHost            = apiConfig.apiHost
  val enableTransactions = apiConfig.enableTransactions
  val enableTiles        = apiConfig.enableTiles

  def listCollections(
      acceptHeader: AcceptHeader
  ): F[Either[Unit, (String, fs2.Stream[F, Byte])]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
      updated = collections map { _.maybeAddTilesLink(enableTiles, apiHost) } map {
        _.updateLinksWithHost(apiConfig)
      }
    } yield {
      val collectionResponse = CollectionsResponse(updated)
      val html               = franklin.html.collections(collectionResponse, apiConfig.apiHost).body
      handleOut[F](html, collectionResponse.asJson, acceptHeader)
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
        collectionOption map { _.maybeAddTilesLink(enableTiles, apiHost) } map {
          _.updateLinksWithHost(apiConfig).asJson
        },
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

  def createCollection(collection: StacCollection): F[Either[Unit, Json]] = {
    val newCollection = collection.copy(links =
      collection.links.filter({ link =>
        !Set[StacLinkType](StacLinkType.Item, StacLinkType.StacRoot, StacLinkType.Self)
          .contains(link.rel)
      }) ++
        List(
          StacLink(
            s"$apiHost/collections/${collection.id}",
            StacLinkType.Self,
            Some(`application/json`),
            collection.title
          ),
          StacLink(
            s"$apiHost",
            StacLinkType.StacRoot,
            Some(`application/json`),
            None
          )
        )
    )
    for {
      inserted <- StacCollectionDao.insertStacCollection(newCollection, None).transact(xa)
    } yield Right(inserted.asJson)
  }

  def deleteCollection(rawCollectionId: String): F[Either[NF, Unit]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionOption <- StacCollectionDao
        .getCollectionUnique(collectionId)
        .transact(xa)
      deleted <- collectionOption traverse { _ =>
        StacCollectionDao.query.filter(fr"id = $collectionId").delete.transact(xa).void
      }
    } yield {
      Either.fromOption(
        deleted,
        NF(s"Collection $collectionId not found")
      )
    }
  }

  val collectionEndpoints = new CollectionEndpoints[F](enableTransactions, enableTiles)

  val routesList = List(
    collectionEndpoints.collectionsList.toRoutes(acceptHeader => listCollections(acceptHeader)),
    collectionEndpoints.collectionUnique
      .toRoutes {
        case collectionId => getCollectionUnique(collectionId)
      }
  ) ++
    (if (enableTiles) {
       List(collectionEndpoints.collectionTiles.toRoutes(getCollectionTiles))
     } else Nil) ++
    (if (enableTransactions) {
       List(
         collectionEndpoints.createCollection
           .toRoutes(collection => createCollection(collection)),
         collectionEndpoints.deleteCollection
           .toRoutes(rawCollectionId => deleteCollection(rawCollectionId))
       )
     } else Nil)

  val routes = routesList.foldK

}
