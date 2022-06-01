package com.azavea.franklin.api.services

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.api.endpoints.ItemEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel._
import com.azavea.franklin.error.{
  CrudError,
  InvalidPatch,
  MidAirCollision,
  NotFound => NF,
  ValidationError
}
import com.azavea.franklin.extensions.validation._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s._
import com.azavea.stac4s.{`application/json`, StacItem, StacLink}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.client.{NothingT, SttpBackend}
import sttp.tapir.DecodeResult
import sttp.tapir.server.DecodeFailureContext
import sttp.tapir.server.ServerDefaults
import sttp.tapir.server.http4s._

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

case class AddItemLinks(apiConfig: ApiConfig) {

  def createSelfLink(item: StacItem): StacLink =
    StacLink(
      s"${apiConfig.apiHost}/collections/${item.collection.get}/items/${item.id}",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )

  def apply(item: StacItem): StacItem = {
    val prunedLinks = item.links.filter { link =>
      link.rel != StacLinkType.Self
    }
    item.copy(links=prunedLinks :+ createSelfLink(item))
  }
}

class ItemService[F[_]: Concurrent](
    xa: Transactor[F],
    apiConfig: ApiConfig,
    itemExtensionsRef: ExtensionRef[F, StacItem],
    rootLink: StacLink
)(
    implicit contextShift: ContextShift[F],
    timer: Timer[F],
    serverOptions: Http4sServerOptions[F],
    backend: SttpBackend[F, Nothing, NothingT],
    logger: Logger[F]
) extends Http4sDsl[F] {

  val apiHost            = apiConfig.apiHost
  val defaultLimit       = apiConfig.defaultLimit
  val enableTransactions = apiConfig.enableTransactions
  val addItemLinks       = AddItemLinks(apiConfig)

  def listItems(
      rawCollectionId: String,
      token: Option[String],
      limit: Option[NonNegInt]
  ): F[Either[Unit, Json]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      items <- PGStacQueries
        .listItems(collectionId, limit.map(_.value).getOrElse(defaultLimit))
        .transact(xa)
    } yield {
      val response = ItemsResponseJson(
        items.toList,
        List()
      )
      Either.right(response.asJson)
    }
  }

  def getItemUnique(
      rawCollectionId: String,
      rawItemId: String
  ): F[Either[NF, (StacItem, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    for {
      itemResults <- PGStacQueries.getItem(collectionId, itemId).transact(xa)
    } yield {
      itemResults match {
        case Some(item) => {
          val itemWithLinks = addItemLinks(item)
          Right((itemWithLinks, itemWithLinks.##.toString))
        }
        case None       => Left(NF(s"Item $itemId in collection $collectionId not found"))
      }
    }
  }

  def postItem(rawCollectionId: String, item: StacItem): F[Either[ValidationError, (StacItem, String)]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    val updatedItemCollection = item.copy(collection=Some(collectionId))
    for {
      _ <- PGStacQueries.createItem(item).transact(xa)
    } yield {
      Right((updatedItemCollection, updatedItemCollection.##.toString))
    }
  }

  def putItem(
      rawCollectionId: String,
      rawItemId: String,
      newItem: StacItem,
      etag: IfMatchMode
  ): F[Either[CrudError, (StacItem, String)]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val updatedItemCollection = newItem.copy(collection=Some(collectionId))
    if (updatedItemCollection.id != itemId) {
      Either.left[CrudError, (StacItem, String)](
        ValidationError(s"ID on stac item (${newItem.id}) doesn't match path ID (${itemId})")
      ).pure[F]
    } else {
      for {
        _ <- PGStacQueries.updateItem(updatedItemCollection).transact(xa)
      } yield {
        Right((updatedItemCollection, updatedItemCollection.##.toString))
      }
    }
  }

  def deleteItem(
      rawCollectionId: String,
      rawItemId: String
  ): F[Either[Unit, Unit]] ={
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    for {
      _ <- PGStacQueries.deleteItem(collectionId, itemId).transact(xa)
    } yield {
      Right(())
    }
  }

  def patchItem(
      rawCollectionId: String,
      rawItemId: String,
      jsonPatch: Json,
      etag: IfMatchMode
  ): F[Either[CrudError, (StacItem, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      dbItem     <- PGStacQueries.getItem(collectionId, itemId).transact(xa)
      merged     = dbItem.map(_.asJson.deepMerge(jsonPatch).deepDropNullValues)
      rehydrated = merged.flatMap(_.as[StacItem].toOption)
      _          <- rehydrated match {
                      case Some(item) => PGStacQueries.updateItem(item).transact(xa)
                      case None => None.pure[F]
                    }
    } yield {
      if (dbItem.isEmpty) {
        Either.left[CrudError, (StacItem, String)](InvalidPatch(s"No item found at collection $collectionId and id $itemId", jsonPatch))
      } else {
        rehydrated match {
          case Some(item) => 
            Either.right[CrudError, (StacItem, String)]((item, item.##.toString))
          case None =>
            Either.left[CrudError, (StacItem, String)](InvalidPatch("Unable to successfully merge patch with item in database", jsonPatch))
        }
      }
    }
  }

  val itemEndpoints =
    new ItemEndpoints(defaultLimit, enableTransactions, apiConfig.path)

  val transactionRoutes: List[HttpRoutes[F]] = List(
    Http4sServerInterpreter.toRoutes(itemEndpoints.postItem)({
      case (collectionId, stacItem) => postItem(collectionId, stacItem)
    }),
    Http4sServerInterpreter.toRoutes(itemEndpoints.putItem)({
      case (collectionId, itemId, stacItem, etag) => putItem(collectionId, itemId, stacItem, etag)
    }),
    Http4sServerInterpreter.toRoutes(itemEndpoints.deleteItem)({
      case (collectionId, itemId) => deleteItem(collectionId, itemId)
    }),
    Http4sServerInterpreter.toRoutes(itemEndpoints.patchItem)({
      case (collectionId, itemId, jsonPatch, etag) =>
        patchItem(collectionId, itemId, jsonPatch, etag)
    })
  )

  val routesList: NonEmptyList[HttpRoutes[F]] = NonEmptyList.of(
    Http4sServerInterpreter.toRoutes(itemEndpoints.itemsList)({ query =>
      Function.tupled(listItems _)(query)
    }),
    Http4sServerInterpreter.toRoutes(itemEndpoints.itemsUnique)({
      case (collectionId, itemId) => getItemUnique(collectionId, itemId)
    })
  ) ++ (if (enableTransactions) {
          transactionRoutes
        } else {
          List.empty
        })

  val routes: HttpRoutes[F] = routesList.foldK
}
