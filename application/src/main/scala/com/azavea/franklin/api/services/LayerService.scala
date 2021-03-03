package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.SearchFilters
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.database.StacLayerDao
import com.azavea.franklin.datamodel.Query
import com.azavea.franklin.datamodel.SearchMethod
import com.azavea.franklin.datamodel.StacSearchCollection
import com.azavea.stac4s.{`application/json`, StacCatalog, StacLink, StacLinkType}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._
import com.azavea.stac4s.extensions.layer.StacLayer
import com.azavea.franklin.error.NotFound
import com.azavea.franklin.error.CrudError
import com.azavea.franklin.datamodel.Superset
import cats.data.NonEmptyVector
import com.azavea.stac4s.StacItem
import cats.data.NonEmptyList
import com.azavea.franklin.database.Page

class LayerService[F[_]: Concurrent](
    apiHost: NonEmptyString,
    xa: Transactor[F]
) {

  // TODO add pagination
  def listLayers: F[Either[Unit, StacCatalog]] = {
    StacLayerDao.query.list.transact(xa) map { layers =>
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
  def getLayerItems(rawLayerId: String, page: Page): F[Either[CrudError, List[StacItem]]] =
    (for {
      layerO <- StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption
      items  <- layerO traverse { layer => StacLayerDao.pageLayerItems(layer, page).compile.toList }
    } yield Either.fromOption(
      items,
      NotFound()
    )).transact(xa).widen

  // TODO expand (?) spatial and temporal extent of layers when adding items
  def addLayerItems(
      rawLayerId: String,
      itemIds: NonEmptyList[NonEmptyString]
  ): F[Either[CrudError, StacLayer]] =
    StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption.transact(xa) flatMap {
      case Some(layer) =>
        fs2.Stream
          .emits(itemIds.toList)
          .covary[F]
          .parEvalMap(10) { (itemId: NonEmptyString) =>
            StacLayerDao.addItem(layer, itemId).transact(xa)
          }
          .compile
          .drain flatMap { _ =>
          StacLayerDao.query.filter(fr"id=${urlDecode(rawLayerId)}").select.transact(xa) map {
            Right(_)
          }
        }
      case None => Either.left[CrudError, StacLayer](NotFound()).pure[F]
    }

  def replaceLayerItems(
      rawLayerId: String,
      itemids: NonEmptyList[String]
  ): F[Either[CrudError, StacLayer]] = ???

  def getLayerItem(rawLayerId: String, rawItemId: String): F[Either[CrudError, StacItem]] = ???

  def removeLayerItem(rawLayerId: String, rawItemId: String): F[Either[CrudError, StacItem]] =
    (StacLayerDao.query.filter(fr"id = ${urlDecode(rawLayerId)}").selectOption flatMap { layerO =>
      layerO flatTraverse { layer => StacLayerDao.removeItem(layer, urlDecode(rawItemId)) }
    }).transact(xa) map {
      Either.fromOption(_, NotFound())
    }
}
