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
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.client.{NothingT, SttpBackend}
import sttp.tapir.DecodeResult
import sttp.tapir.server.DecodeFailureContext
import sttp.tapir.server.ServerDefaults
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CollectionItemsService[F[_]: Concurrent](
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
  val enableTiles        = apiConfig.enableTiles

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
      validators <- items traverse { item =>
        makeItemValidator(item.stacExtensions, itemExtensionsRef)
      }
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
        updatedItems.zip(validators) map { case (item, f) => f(item.addRootLink(rootLink)) }
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
      validatorOption <- itemOption traverse { item =>
        makeItemValidator(item.stacExtensions, itemExtensionsRef)
      }
    } yield {
      Either.fromOption(
        (itemOption, validatorOption).tupled map {
          case (item, validator) =>
            val updatedItem = (if (enableTiles) { item.addTilesLink(apiHost, collectionId, itemId) }
                               else { item })
            (
              validator(updatedItem)
                .addRootLink(rootLink)
                .updateLinksWithHost(apiConfig)
                .asJson,
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
  ): F[Either[NF, Json]] = {
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
              .map(_.asJson),
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
    val collectionNotFound: String => Either[ValidationError, (Json, String)] = (s: String) =>
      Left(
        ValidationError(s"Cannot create an item in non-existent collection: $s")
      )
    makeItemValidator(item.stacExtensions, itemExtensionsRef) flatMap { validator =>
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
            StacItemDao.insertStacItem(withParent).transact(xa) map {
              case Right((inserted, etag)) =>
                val validated = validator(inserted)
                Right((validated.asJson, etag))
              case Left(StacItemDao.InvalidTimeForPeriod) =>
                Left(ValidationError(StacItemDao.InvalidTimeForPeriod.msg))
              // this fall through covers the only other failure mode for item creation
              case Left(_) =>
                collectionNotFound(collId)
            }
          } else {
            Left(
              ValidationError(
                s"Collection ID in item $collId did not match collection ID in route $collectionId"
              )
            ).pure[F].widen
          }
        case None =>
          val withParent =
            item
              .copy(links = fallbackCollectionLink +: item.links, collection = Some(collectionId))
              .addRootLink(rootLink)
          StacItemDao.insertStacItem(withParent).transact(xa) map {
            case Right((inserted, etag)) =>
              val validated = validator(inserted)
              Right((validated.asJson, etag))
            case Left(StacItemDao.InvalidTimeForPeriod) =>
              Left(ValidationError(StacItemDao.InvalidTimeForPeriod.msg))
            // this fall through covers the only other failure mode for item creation
            case Left(_) =>
              collectionNotFound(collectionId)
          }
      }
    }

  }

  def putItem(
      rawCollectionId: String,
      rawItemId: String,
      itemUpdate: StacItem,
      etag: IfMatchMode
  ): F[Either[CrudError, (Json, String)]] = {
    val itemId       = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8.toString)
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    makeItemValidator(itemUpdate.stacExtensions, itemExtensionsRef) flatMap { validator =>
      StacItemDao.updateStacItem(collectionId, itemId, itemUpdate, etag).transact(xa) map {
        case Left(StacItemDao.StaleObject) =>
          Left(MidAirCollision(s"Item $itemId changed server side. Refresh object and try again"))
        case Left(StacItemDao.ItemNotFound) =>
          Left(NF(s"Item $itemId in collection $collectionId not found"))
        case Left(StacItemDao.InvalidTimeForPeriod) =>
          Left(ValidationError(StacItemDao.InvalidTimeForPeriod.msg))
        case Left(_) =>
          Left(ValidationError(s"Update of $itemId not possible with value passed"))
        case Right((item, etag)) =>
          Right((validator(item).addRootLink(rootLink).asJson, etag))
      }
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
      etag: IfMatchMode
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
      .transact(xa) flatMap {
      case None | Some(Left(StacItemDao.ItemNotFound)) |
          Some(Left(StacItemDao.CollectionNotFound)) =>
        Either
          .left[CrudError, (Json, String)](
            NF(s"Item $itemId in collection $collectionId not found")
          )
          .pure[F]
      case Some(Left(StacItemDao.StaleObject)) =>
        Either
          .left[CrudError, (Json, String)](
            MidAirCollision(s"Item $itemId changed server side. Refresh object and try again")
          )
          .pure[F]
      case Some(Left(StacItemDao.UpdateFailed)) =>
        Either
          .left[CrudError, (Json, String)](
            ValidationError(
              s"Update of $itemId not possible with value passed"
            )
          )
          .pure[F]
      case Some(Left(StacItemDao.PatchInvalidatesItem(err))) =>
        Either
          .left[CrudError, (Json, String)](
            InvalidPatch(
              s"Patch would invalidate item $itemId: ${CursorOp.opsToPath(err.history)}",
              jsonPatch
            )
          )
          .pure[F]
      case Some(Left(StacItemDao.InvalidTimeForPeriod)) =>
        // can't match the error in the pattern, because we get a warning about the type
        // not definitely being as narrow as we think it is which refers to
        // https://github.com/scala/bug/issues/1503
        // anyway, it's a case object, so we can just grab the same single object and
        // use its message
        Either
          .left[CrudError, (Json, String)](
            InvalidPatch(
              StacItemDao.InvalidTimeForPeriod.msg,
              jsonPatch
            )
          )
          .pure[F]
      case Some(Right((updated, etag))) =>
        makeItemValidator(updated.stacExtensions, itemExtensionsRef) map { validator =>
          Either.right(
            (
              validator(updated).addRootLink(rootLink).asJson,
              etag
            )
          )
        }

    }
  }

  val collectionItemEndpoints =
    new CollectionItemEndpoints(defaultLimit, enableTransactions, enableTiles, apiConfig.path)

  val collectionItemTileRoutes =
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.collectionItemTiles)({
      case (collectionId, itemId) => getCollectionItemTileInfo(collectionId, itemId)
    })

  val transactionRoutes: List[HttpRoutes[F]] = List(
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.postItem)({
      case (collectionId, stacItem) => postItem(collectionId, stacItem)
    }),
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.putItem)({
      case (collectionId, itemId, stacItem, etag) => putItem(collectionId, itemId, stacItem, etag)
    }),
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.deleteItem)({
      case (collectionId, itemId) => deleteItem(collectionId, itemId)
    }),
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.patchItem)({
      case (collectionId, itemId, jsonPatch, etag) =>
        patchItem(collectionId, itemId, jsonPatch, etag)
    })
  )

  val routesList: NonEmptyList[HttpRoutes[F]] = NonEmptyList.of(
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.collectionItemsList)({ query =>
      Function.tupled(listCollectionItems _)(query)
    }),
    Http4sServerInterpreter.toRoutes(collectionItemEndpoints.collectionItemsUnique)({
      case (collectionId, itemId) => getCollectionItemUnique(collectionId, itemId)
    })
  ) ++ (if (enableTransactions) {
          transactionRoutes
        } else {
          List.empty
        }) ++ (if (enableTiles) {
                 List(collectionItemTileRoutes)
               } else { List.empty })

  val routes: HttpRoutes[F] = routesList.foldK
}
