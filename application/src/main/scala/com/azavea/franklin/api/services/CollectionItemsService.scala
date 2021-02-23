package com.azavea.franklin.api.services

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints.CollectionItemEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.Filterables._
import com.azavea.franklin.database.Page
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.datamodel._
import com.azavea.franklin.error.{
  CrudError,
  InvalidPatch,
  MidAirCollision,
  NotFound => NF,
  ValidationError
}
import com.azavea.franklin.extensions.validation.{validateItemAndLinks, ExtensionName}
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s._
import com.azavea.stac4s.{`application/json`, StacItem, StacLink}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.tapir.DecodeResult
import sttp.tapir.server.DecodeFailureContext
import sttp.tapir.server.ServerDefaults
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CollectionItemsService[F[_]: Concurrent](
    xa: Transactor[F],
    apiConfig: ApiConfig
)(
    implicit contextShift: ContextShift[F],
    timer: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  val apiHost            = apiConfig.apiHost
  val defaultLimit       = apiConfig.defaultLimit
  val enableTransactions = apiConfig.enableTransactions
  val enableTiles        = apiConfig.enableTiles

  def listCollectionItems(
      collectionId: String,
      token: Option[PaginationToken],
      limit: Option[NonNegInt],
      extensions: List[ExtensionName]
  ): F[Either[Unit, Json]] = {
    val decodedId = URLDecoder.decode(collectionId, StandardCharsets.UTF_8.toString)
    for {
      items <- StacItemDao.query
        .filter(StacItemDao.collectionFilter(decodedId))
        .filter(extensions)
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
      val withValidatedLinks =
        updatedItems map { validateItemAndLinks }
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
      val response = CollectionItemsResponse(
        withValidatedLinks.map(_.updateLinksWithHost(apiConfig)),
        nextLink.toList
      )
      Either.right(response.asJson)
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
          (
            validateItemAndLinks(updatedItem).updateLinksWithHost(apiConfig).asJson,
            item.##.toString
          )
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
      s"/collections/$collectionId",
      StacLinkType.Parent,
      Some(`application/json`),
      Some("Parent collection")
    )
    item.collection match {
      case Some(collId) =>
        if (collectionId == collId) {
          val parentLink = item.links.filter(_.rel == StacLinkType.Parent).headOption map {
            existingLink => existingLink.copy(href = s"/collections/$collectionId")
          } getOrElse {
            fallbackCollectionLink
          }
          val withParent =
            item.copy(links = parentLink +: item.links.filter(_.rel != StacLinkType.Parent))
          StacItemDao.insertStacItem(withParent).transact(xa) map { inserted =>
            val validated = validateItemAndLinks(inserted)
            Right((validated.asJson, validated.##.toString))
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
        val withParent =
          item.copy(links = fallbackCollectionLink +: item.links, collection = Some(collectionId))
        StacItemDao.insertStacItem(withParent).transact(xa) map { inserted =>
          val validated = validateItemAndLinks(inserted)
          Right((validated.asJson, validated.##.toString))
        }
    }
  }

  def putItem(
      rawCollectionId: String,
      rawItemId: String,
      itemUpdate: StacItem,
      etag: String
  ): F[Either[CrudError, (Json, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    StacItemDao.updateStacItem(collectionId, itemId, itemUpdate, etag).transact(xa) map {
      case Left(StacItemDao.StaleObject) =>
        Left(MidAirCollision(s"Item $itemId changed server side. Refresh object and try again"))
      case Left(StacItemDao.ItemNotFound) =>
        Left(NF(s"Item $itemId in collection $collectionId not found"))
      case Left(_) =>
        Left(ValidationError(s"Update of $itemId not possible with value passed"))
      case Right(item) =>
        Right((validateItemAndLinks(item).asJson, item.##.toString))
    }
  }

  def deleteItem(
      rawCollectionId: String,
      rawItemId: String
  ): F[Either[Unit, Unit]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    StacItemDao.query
      .filter(fr"id = $itemId")
      .filter(StacItemDao.collectionFilter(collectionId))
      .delete
      .transact(xa) *> Applicative[F].pure { Right(()) }
  }

  def patchItem(
      rawCollectionId: String,
      rawItemId: String,
      jsonPatch: Json,
      etag: String
  ): F[Either[CrudError, (Json, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    StacItemDao
      .patchItem(
        collectionId,
        itemId,
        jsonPatch,
        etag
      )
      .transact(xa) map {
      case None | Some(Left(StacItemDao.ItemNotFound)) =>
        Left(NF(s"Item $itemId in collection $collectionId not found"))
      case Some(Left(StacItemDao.StaleObject)) =>
        Left(MidAirCollision(s"Item $itemId changed server side. Refresh object and try again"))
      case Some(Left(StacItemDao.UpdateFailed)) =>
        Left(ValidationError(s"Update of $itemId not possible with value passed"))
      case Some(Left(StacItemDao.PatchInvalidatesItem(err))) =>
        Left(
          InvalidPatch(
            s"Patch would invalidate item $itemId: ${CursorOp.opsToPath(err.history)}",
            jsonPatch
          )
        )
      case Some(Right(updated)) =>
        Right((validateItemAndLinks(updated).asJson, updated.##.toString))

    }
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
