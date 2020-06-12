package com.azavea.franklin.api.services

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.endpoints.CollectionItemEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.Filterables._
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.datamodel._
import com.azavea.franklin.error.{
  CrudError,
  InvalidPatch,
  MidAirCollision,
  NotFound => NF,
  ValidationError
}
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s._
import com.azavea.stac4s.{`application/json`, StacItem, StacLink}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.azavea.franklin.database.Page

class CollectionItemsService[F[_]: Sync](
    xa: Transactor[F],
    apiHost: NonEmptyString,
    defaultLimit: NonNegInt,
    enableTransactions: Boolean,
    enableTiles: Boolean
)(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def listCollectionItems(
      collectionId: String,
      token: Option[PaginationToken],
      limit: Option[NonNegInt]
  ): F[Either[Unit, Json]] = {
    val decodedId = URLDecoder.decode(collectionId, StandardCharsets.UTF_8.toString)
    for {
      items <- StacItemDao.query
        .filter(StacItemDao.collectionFilter(decodedId))
        .page(Page(limit getOrElse defaultLimit, token))
        .transact(xa)
      paginationToken <- items.lastOption traverse { item =>
        StacItemDao.getPaginationToken(item.id).transact(xa)
      }
    } yield {
      val updatedItems = enableTiles match {
        case true =>
          items map { item => item.addTilesLink(apiHost, collectionId, item.id) }
        case _ => items
      }
      val nextLink: Option[StacLink] = paginationToken.flatten map { token =>
        val lim = limit getOrElse defaultLimit
        StacLink(
          href = s"$apiHost/collections/$collectionId/items?next=${PaginationToken
            .encPaginationToken(token)}&limit=$lim",
          rel = StacLinkType.Next,
          _type = Some(`application/json`),
          title = None
        )
      }
      val response = Json.obj(
        "type"     -> Json.fromString("FeatureCollection"),
        "features" -> updatedItems.asJson,
        "links"    -> nextLink.toList.asJson
      )
      Either.right(response)
    }

  }

  def getCollectionItemUnique(
      rawCollectionId: String,
      rawItemId: String
  ): F[Either[NF, (Json, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    for {
      itemOption <- StacItemDao.getCollectionItem(collectionId, itemId).transact(xa)
    } yield {
      Either.fromOption(
        itemOption map { item =>
          val updatedItem = (if (enableTiles) { item.addTilesLink(apiHost, collectionId, itemId) }
                             else { item })
          (updatedItem.asJson, item.##.toString)
        },
        NF(s"Item $itemId in collection $collectionId not found")
      )
    }
  }

  def getCollectionItemTileInfo(
      rawCollectionId: String,
      rawItemId: String
  ): F[Either[NF, (Json, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    for {
      itemOption <- StacItemDao.getCollectionItem(collectionId, itemId).transact(xa)
    } yield {
      itemOption match {
        case Some(item) =>
          Either.fromOption(
            TileInfo
              .fromStacItem(apiHost, collectionId, item)
              .map(info => (info.asJson, info.##.toString)),
            NF(
              s"Unable to construct tile info object for item $itemId in collection $collectionId. Is there at least one COG asset?"
            )
          )
        case None => Either.left(NF(s"Item $itemId in collection $collectionId not found"))
      }
    }
  }

  def postItem(collectionId: String, item: StacItem): F[Either[ValidationError, (Json, String)]] = {
    val fallbackCollectionLink = StacLink(
      s"$apiHost/collections/$collectionId",
      StacLinkType.Parent,
      Some(`application/json`),
      Some("Parent collection")
    )
    item.collection match {
      case Some(collId) =>
        if (collectionId == collId) {
          val parentLink = item.links.filter(_.rel == StacLinkType.Parent).headOption map {
            existingLink => existingLink.copy(href = s"$apiHost/api/collections/$collectionId")
          } getOrElse {
            fallbackCollectionLink
          }
          val withParent =
            item.copy(links = parentLink +: item.links.filter(_.rel != StacLinkType.Parent))
          StacItemDao.insertStacItem(withParent).transact(xa) map { inserted =>
            Right((inserted.asJson, inserted.##.toString))
          }
        } else {
          Applicative[F].pure(
            Left(
              ValidationError(
                s"Collection ID in item $collId did not match collection ID in route $collectionId"
              )
            )
          )
        }
      case None =>
        val withParent = item.copy(links = fallbackCollectionLink +: item.links)
        StacItemDao.insertStacItem(withParent).transact(xa) map { inserted =>
          Right((inserted.asJson, inserted.##.toString))
        }
    }
  }

  def putItem(
      collectionId: String,
      itemId: String,
      itemUpdate: StacItem,
      etag: String
  ): F[Either[CrudError, (Json, String)]] =
    StacItemDao.updateStacItem(collectionId, itemId, itemUpdate, etag).transact(xa) map {
      case Left(StacItemDao.StaleObject) =>
        Left(MidAirCollision(s"Item $itemId changed server side. Refresh object and try again"))
      case Left(StacItemDao.ItemNotFound) =>
        Left(NF(s"Item $itemId in collection $collectionId not found"))
      case Left(_) =>
        Left(ValidationError(s"Update of $itemId not possible with value passed"))
      case Right(item) =>
        Right((item.asJson, item.##.toString))
    }

  def deleteItem(
      collectionId: String,
      itemId: String
  ): F[Either[Unit, Unit]] =
    StacItemDao.query
      .filter(fr"id = $itemId")
      .filter(StacItemDao.collectionFilter(collectionId))
      .delete
      .transact(xa) *> Applicative[F].pure { Right(()) }

  def patchItem(
      collectionId: String,
      itemId: String,
      jsonPatch: Json,
      etag: String
  ): F[Either[CrudError, (Json, String)]] =
    StacItemDao
      .patchItem(
        collectionId,
        itemId,
        jsonPatch,
        etag
      )
      .transact(xa) map {
      case Left(StacItemDao.StaleObject) =>
        Left(MidAirCollision(s"Item $itemId changed server side. Refresh object and try again"))
      case Left(StacItemDao.ItemNotFound) =>
        Left(NF(s"Item $itemId in collection $collectionId not found"))
      case Left(StacItemDao.UpdateFailed) =>
        Left(ValidationError(s"Update of $itemId not possible with value passed"))
      case Left(StacItemDao.PatchInvalidatesItem(err)) =>
        Left(InvalidPatch(s"Patch would invalidate item $itemId", jsonPatch, err))
      case Right(updated) =>
        Right((updated.asJson, updated.##.toString))
    }

  val collectionItemEndpoints =
    new CollectionItemEndpoints(defaultLimit, enableTransactions, enableTiles)

  val collectionItemTileRoutes = collectionItemEndpoints.collectionItemTiles.toRoutes {
    case (collectionId, itemId) => getCollectionItemTileInfo(collectionId, itemId)
  }

  val transactionRoutes: List[HttpRoutes[F]] = List(
    collectionItemEndpoints.postItem.toRoutes {
      case (collectionId, stacItem) => postItem(collectionId, stacItem)
    },
    collectionItemEndpoints.putItem.toRoutes {
      case (collectionId, itemId, stacItem, etag) => putItem(collectionId, itemId, stacItem, etag)
    },
    collectionItemEndpoints.deleteItem.toRoutes {
      case (collectionId, itemId) => deleteItem(collectionId, itemId)
    },
    collectionItemEndpoints.patchItem.toRoutes {
      case (collectionId, itemId, jsonPatch, etag) =>
        patchItem(collectionId, itemId, jsonPatch, etag)
    }
  )

  val routesList: NonEmptyList[HttpRoutes[F]] = NonEmptyList.of(
    collectionItemEndpoints.collectionItemsList.toRoutes { query =>
      Function.tupled(listCollectionItems _)(query)
    },
    collectionItemEndpoints.collectionItemsUnique.toRoutes {
      case (collectionId, itemId) => getCollectionItemUnique(collectionId, itemId)
    }
  ) ++ (if (enableTransactions) {
          transactionRoutes
        } else {
          List.empty
        }) ++ (if (enableTiles) {
                 List(collectionItemTileRoutes)
               } else { List.empty })

  val routes: HttpRoutes[F] = routesList.foldK
}
