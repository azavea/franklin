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

  val _itemId       = root.id.string
  val _collectionId = root.collection.string

  def _addLink(link: StacLink) = root.links.arr.modify({ ls: Vector[Json] => ls :+ link.asJson })

  def addTileLink(item: Json): Json = item

  // def addTileLink(item: Json): Json = {
  //   val encodedCollectionId =
  //     URLEncoder.encode(_collectionId.getOption(item).get, StandardCharsets.UTF_8.toString)
  //   val tileLink = StacLink(
  //     s"${apiConfig.apiHost}/collections/$encodedCollectionId/tiles",
  //     StacLinkType.VendorLinkType("tiles"),
  //     Some(`application/json`),
  //     Some("Tile URLs for Collection")
  //   )
  //   _addLink(tileLink)(collection)
  // }

  def addSelfLink(item: Json): Json = {
    val encodedCollectionId =
      URLEncoder.encode(_collectionId.getOption(item).get, StandardCharsets.UTF_8.toString)
    val encodedItemId =
      URLEncoder.encode(_itemId.getOption(item).get, StandardCharsets.UTF_8.toString)
    val selfLink = StacLink(
      s"${apiConfig.apiHost}/collections/$encodedCollectionId/items/$encodedItemId",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )
    _addLink(selfLink)(item)
  }

  def apply(item: Json) = {
    (addSelfLink _ compose addTileLink)(item)
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
  val addItemLinks       = AddCollectionLinks(apiConfig)

  def listItems(
      collectionId: String,
      token: Option[String],
      limit: Option[NonNegInt]
  ): F[Either[Unit, Json]] = {
    val decodedId = URLDecoder.decode(collectionId, StandardCharsets.UTF_8.toString)
    for {
      items <- PGStacQueries
        .listItems(decodedId, limit.map(_.value).getOrElse(defaultLimit))
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
        case Some(item) => Right((item, item.##.toString))
        case None       => Left(NF(s"Item $itemId in collection $collectionId not found"))
      }
    }
  }

  def postItem(collectionId: String, item: StacItem): F[Either[ValidationError, (Json, String)]] =
    ???
  // {
  //   val fallbackCollectionLink = StacLink(
  //     s"/collections/$collectionId",
  //     StacLinkType.Parent,
  //     Some(`application/json`),
  //     Some("Parent collection")
  //   )
  //   val collectionNotFound: String => Either[ValidationError, (Json, String)] = (s: String) =>
  //     Left(
  //       ValidationError(s"Cannot create an item in non-existent collection: $s")
  //     )
  //   makeItemValidator(item.stacExtensions, itemExtensionsRef) flatMap { validator =>
  //     item.collection match {
  //       case Some(collId) =>
  //         if (collectionId == collId) {
  //           val parentLink = item.links.filter(_.rel == StacLinkType.Parent).headOption map {
  //             existingLink => existingLink.copy(href = s"/collections/$collectionId")
  //           } getOrElse {
  //             fallbackCollectionLink
  //           }
  //           val withParent =
  //             item.copy(links = parentLink +: item.links.filter(_.rel != StacLinkType.Parent))
  //           StacItemDao.insertStacItem(withParent).transact(xa) map {
  //             case Right((inserted, etag)) =>
  //               val validated = validator(inserted)
  //               Right((validated.asJson, etag))
  //             case Left(StacItemDao.InvalidTimeForPeriod) =>
  //               Left(ValidationError(StacItemDao.InvalidTimeForPeriod.msg))
  //             // this fall through covers the only other failure mode for item creation
  //             case Left(_) =>
  //               collectionNotFound(collId)
  //           }
  //         } else {
  //           Left(
  //             ValidationError(
  //               s"Collection ID in item $collId did not match collection ID in route $collectionId"
  //             )
  //           ).pure[F].widen
  //         }
  //       case None =>
  //         val withParent =
  //           item
  //             .copy(links = fallbackCollectionLink +: item.links, collection = Some(collectionId))
  //             .addRootLink(rootLink)
  //         StacItemDao.insertStacItem(withParent).transact(xa) map {
  //           case Right((inserted, etag)) =>
  //             val validated = validator(inserted)
  //             Right((validated.asJson, etag))
  //           case Left(StacItemDao.InvalidTimeForPeriod) =>
  //             Left(ValidationError(StacItemDao.InvalidTimeForPeriod.msg))
  //           // this fall through covers the only other failure mode for item creation
  //           case Left(_) =>
  //             collectionNotFound(collectionId)
  //         }
  //     }
  //   }

  // }

  def putItem(
      rawCollectionId: String,
      rawItemId: String,
      itemUpdate: StacItem,
      etag: IfMatchMode
  ): F[Either[CrudError, (Json, String)]] = ???
  // {
  //   val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
  //   val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

  //   makeItemValidator(itemUpdate.stacExtensions, itemExtensionsRef) flatMap { validator =>
  //     StacItemDao.updateStacItem(collectionId, itemId, itemUpdate, etag).transact(xa) map {
  //       case Left(StacItemDao.StaleObject(etag, item)) =>
  //         Left(
  //           MidAirCollision(
  //             s"Item $itemId changed server side. Refresh object and try again",
  //             etag,
  //             item
  //           )
  //         )
  //       case Left(StacItemDao.ItemNotFound) =>
  //         Left(NF(s"Item $itemId in collection $collectionId not found"))
  //       case Left(StacItemDao.InvalidTimeForPeriod) =>
  //         Left(ValidationError(StacItemDao.InvalidTimeForPeriod.msg))
  //       case Left(_) =>
  //         Left(ValidationError(s"Update of $itemId not possible with value passed"))
  //       case Right((item, etag)) =>
  //         Right((validator(item).addRootLink(rootLink).asJson, etag))
  //     }
  //   }
  // }

  def deleteItem(
      rawCollectionId: String,
      rawItemId: String
  ): F[Either[Unit, Unit]] = ???
  // {
  //   val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
  //   val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

  //   StacItemDao.query
  //     .filter(fr"id = $itemId")
  //     .filter(StacItemDao.collectionFilter(collectionId))
  //     .delete
  //     .transact(xa) *> Applicative[F].pure { Right(()) }
  // }

  def patchItem(
      rawCollectionId: String,
      rawItemId: String,
      jsonPatch: Json,
      etag: IfMatchMode
  ): F[Either[CrudError, (Json, String)]] = ???
  // {
  //   val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
  //   val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

  //   StacItemDao
  //     .patchItem(
  //       collectionId,
  //       itemId,
  //       jsonPatch,
  //       etag
  //     )
  //     .transact(xa) flatMap {
  //     case None | Some(Left(StacItemDao.ItemNotFound)) |
  //         Some(Left(StacItemDao.CollectionNotFound)) =>
  //       Either
  //         .left[CrudError, (Json, String)](
  //           NF(s"Item $itemId in collection $collectionId not found")
  //         )
  //         .pure[F]
  //     case Some(Left(StacItemDao.StaleObject(etag, item))) =>
  //       Either
  //         .left[CrudError, (Json, String)](
  //           MidAirCollision(
  //             s"Item $itemId changed server side. Refresh object and try again",
  //             etag,
  //             item
  //           )
  //         )
  //         .pure[F]
  //     case Some(Left(StacItemDao.UpdateFailed)) =>
  //       Either
  //         .left[CrudError, (Json, String)](
  //           ValidationError(
  //             s"Update of $itemId not possible with value passed"
  //           )
  //         )
  //         .pure[F]
  //     case Some(Left(StacItemDao.PatchInvalidatesItem(err))) =>
  //       Either
  //         .left[CrudError, (Json, String)](
  //           InvalidPatch(
  //             s"Patch would invalidate item $itemId: ${CursorOp.opsToPath(err.history)}",
  //             jsonPatch
  //           )
  //         )
  //         .pure[F]
  //     case Some(Left(StacItemDao.InvalidTimeForPeriod)) =>
  //       // can't match the error in the pattern, because we get a warning about the type
  //       // not definitely being as narrow as we think it is which refers to
  //       // https://github.com/scala/bug/issues/1503
  //       // anyway, it's a case object, so we can just grab the same single object and
  //       // use its message
  //       Either
  //         .left[CrudError, (Json, String)](
  //           InvalidPatch(
  //             StacItemDao.InvalidTimeForPeriod.msg,
  //             jsonPatch
  //           )
  //         )
  //         .pure[F]
  //     case Some(Right((updated, etag))) =>
  //       makeItemValidator(updated.stacExtensions, itemExtensionsRef) map { validator =>
  //         Either.right(
  //           (
  //             validator(updated).addRootLink(rootLink).asJson,
  //             etag
  //           )
  //         )
  //       }

  //   }
  // }

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
