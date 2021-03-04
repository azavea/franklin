package com.azavea.franklin.api.services

import cats.data.NonEmptyList
import cats.data.NonEmptyVector
import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints.LayerEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.Page
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.database.StacLayerDao
import com.azavea.franklin.datamodel.{
  CollectionItemsResponse,
  PaginationToken,
  Query,
  SearchMethod,
  StacSearchCollection,
  Superset
}
import com.azavea.franklin.error.{CrudError, NotFound}
import com.azavea.stac4s.{`application/json`, StacCatalog, StacItem, StacLink, StacLinkType}
import com.azavea.stac4s.extensions.layer.StacLayer
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

class LayerService[F[_]: Concurrent](
    apiConfig: ApiConfig,
    xa: Transactor[F]
)(
    implicit serverOptions: Http4sServerOptions[F],
    contextShift: ContextShift[F],
    timer: Timer[F]
) {

  val apiHost = apiConfig.apiHost
  // TODO add to API config
  val enableLayers = true
  val defaultLimit = apiConfig.defaultLimit

  val endpoints = new LayerEndpoints[F](enableLayers, defaultLimit)

  // TODO add pagination
  def listLayers(
      paginationToken: Option[PaginationToken],
      limit: Option[numeric.NonNegInt]
  ): F[Either[Unit, StacCatalog]] = {
    StacLayerDao.query.page(Page(limit getOrElse defaultLimit, paginationToken)).transact(xa) map {
      layers =>
        Right(
          StacCatalog(
            "1.0.0-beta2",
            List("layers"),
            "layers-response",
            None,
            "Available layers",
            layers map { layer =>
              StacLink(
                s"$apiHost/layers/${urlEncode(layer.id)}",
                StacLinkType.Child,
                Some(`application/json`),
                None
              )
            }
          )
        )
    }
  }

  def createLayer(stacLayer: StacLayer): F[Either[Unit, StacLayer]] =
    StacLayerDao.createLayer(stacLayer).transact(xa) map { Right(_) }

  def getLayer(rawLayerId: String): F[Either[CrudError, StacLayer]] =
    (StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption flatMap {
      case None => Either.left[CrudError, StacLayer](NotFound()).pure[ConnectionIO]
      case Some(layer) =>
        (StacLayerDao.streamLayerItems(layer) map { item =>
          StacLink(
            s"$apiHost/layers/${layer.id}/items/${item.id}",
            StacLinkType.Item,
            Some(`application/json`),
            None
          )
        }).compile.toList map { itemLinks =>
          Either.right[CrudError, StacLayer](
            layer.copy(
              links = (layer.links ++ itemLinks).distinct
            )
          )
        }
    }).transact(xa)

  def deleteLayer(rawLayerId: String): F[Either[CrudError, Unit]] =
    StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").delete.transact(xa) map {
      case 0 => Left(NotFound())
      case _ => Right(())
    }

  // TODO: add pagination
  def getLayerItems(
      rawLayerId: String,
      token: Option[PaginationToken],
      limit: Option[numeric.NonNegInt]
  ): F[Either[CrudError, CollectionItemsResponse]] =
    (for {
      layerO <- StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption
      items <- layerO traverse { layer =>
        StacLayerDao.pageLayerItems(layer, Page(limit getOrElse defaultLimit, token)).compile.toList
      }
    } yield Either.fromOption(
      items map { CollectionItemsResponse(_, links = Nil) },
      NotFound()
    )).transact(xa).widen

  // TODO expand (?) spatial and temporal extent of layers when adding items
  // TODO include items in layer links
  // TODO include next page link in response
  def addLayerItems(
      rawLayerId: String,
      itemIds: NonEmptyList[String]
  ): F[Either[CrudError, StacLayer]] =
    StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption.transact(xa) flatMap {
      case Some(layer) =>
        fs2.Stream
          .emits(itemIds.toList)
          .covary[F]
          .parEvalMap(10) { (itemId: String) => StacLayerDao.addItem(layer, itemId).transact(xa) }
          .compile
          .drain flatMap { _ =>
          StacLayerDao.query.filter(fr"id=${urlDecode(rawLayerId)}").select.transact(xa) map {
            Right(_)
          }
        }
      case None => Either.left[CrudError, StacLayer](NotFound()).pure[F]
    }

  // TODO expand (?) spatial and temporal extent of layers when adding items
  // TODO include items in layer links
  def replaceLayerItems(
      rawLayerId: String,
      itemIds: NonEmptyList[String]
  ): F[Either[CrudError, StacLayer]] =
    StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption.transact(xa) flatMap {
      case Some(layer) =>
        fs2.Stream
          .emits(itemIds.toList)
          .covary[F]
          .parEvalMap(10) { (itemId: String) => StacLayerDao.addItem(layer, itemId).transact(xa) }
          .compile
          .drain flatMap { _ =>
          StacLayerDao.query.filter(fr"id=${urlDecode(rawLayerId)}").select.transact(xa) map {
            Right(_)
          }
        }
      case None => Either.left[CrudError, StacLayer](NotFound()).pure[F]
    }

  def getLayerItem(rawLayerId: String, rawItemId: String): F[Either[CrudError, StacItem]] =
    (for {
      layerO <- StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption
      itemO <- layerO flatTraverse { layer =>
        StacLayerDao.getLayerItem(layer, urlDecode(rawItemId))
      }
    } yield itemO).transact(xa) map {
      Either.fromOption(
        _,
        NotFound()
      )
    }

  def removeLayerItem(rawLayerId: String, rawItemId: String): F[Either[CrudError, StacLayer]] =
    (StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption flatMap { layerO =>
      layerO flatTraverse { layer => StacLayerDao.removeItem(layer, urlDecode(rawItemId)) }
    }).transact(xa) map {
      Either.fromOption(_, NotFound())
    }

  def routes = if (enableLayers) {
    Http4sServerInterpreter.toRoutes(endpoints.listLayers)({
      case (tokenO, limit) => listLayers(tokenO, limit)
    }) <+>
      Http4sServerInterpreter.toRoutes(endpoints.createLayer)(createLayer) <+>
      Http4sServerInterpreter.toRoutes(endpoints.getLayer)(getLayer) <+>
      Http4sServerInterpreter.toRoutes(endpoints.deleteLayer)(deleteLayer) <+>
      Http4sServerInterpreter.toRoutes(endpoints.getLayerItems)({
        case (layerId, tokenO, limit) => getLayerItems(layerId, tokenO, limit)
      }) <+>
      Http4sServerInterpreter.toRoutes(endpoints.addLayerItems)({
        case (layerId, itemIds) => addLayerItems(layerId, itemIds)
      }) <+>
      Http4sServerInterpreter.toRoutes(endpoints.replaceLayerItems)({
        case (layerId, itemIds) => replaceLayerItems(layerId, itemIds)
      }) <+>
      Http4sServerInterpreter.toRoutes(endpoints.getLayerItem)({
        case (layerId, itemId) => getLayerItem(layerId, itemId)
      }) <+> Http4sServerInterpreter.toRoutes(endpoints.removeLayerItem)({
      case (layerId, itemId) => removeLayerItem(layerId, itemId)
    })
  }
}
