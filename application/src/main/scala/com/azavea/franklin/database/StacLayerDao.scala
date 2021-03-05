package com.azavea.franklin.database

import cats.data.NonEmptyList
import cats.data.NonEmptyVector
import cats.data.Validated
import cats.data.{EitherT, OptionT}
import cats.syntax.all._
import com.azavea.franklin.database
import com.azavea.franklin.datamodel.Superset
import com.azavea.franklin.datamodel.{Context, PaginationToken, SearchMethod, StacSearchCollection}
import com.azavea.franklin.extensions.paging.PagingLinkExtension
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.extensions.layer.StacLayer
import com.azavea.stac4s.extensions.layer.StacLayerProperties
import com.azavea.stac4s.syntax._
import doobie.Fragment
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.refined.implicits._
import doobie.util.Get
import doobie.util.update.Update
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Geometry, Projected}
import io.circe.syntax._
import io.circe.{DecodingFailure, Json}

import java.time.Instant

object StacLayerDao extends Dao[StacLayer] {
  val tableName = "layers"

  val selectF = fr"SELECT id, geom, properties, links FROM" ++ tableF

  private def layerQuery(layer: StacLayer) = SearchFilters(
    None,
    None,
    None,
    Nil,
    Nil,
    None,
    Map("layer:ids" -> List(Superset(NonEmptyVector.of(layer.id.toString.asJson)))),
    None
  )

  def createLayer(layer: StacLayer): ConnectionIO[StacLayer] =
    fr"""
    INSERT INTO layers (id, geom, properties, links) VALUES (
      ${layer.id}, ${layer.geometry}, ${layer.properties}, ${layer.links}
    )""".update
      .withUniqueGeneratedKeys[
        (String, Projected[Geometry], StacLayerProperties, List[StacLink])
      ]("id", "geom", "properties", "links")
      .map({
        case (_1, _2, _3, _4) => StacLayer(NonEmptyString.unsafeFrom(_1), _2, _3, _4)
      })

  def streamLayerItems(layer: StacLayer): fs2.Stream[ConnectionIO, StacItem] =
    StacItemDao.query
      .filter(layerQuery(layer))
      .stream

  def pageLayerItems(layer: StacLayer, page: Page): fs2.Stream[ConnectionIO, StacItem] =
    StacItemDao.query.filter(layerQuery(layer)).pageStream(page)

  def addItem(layer: StacLayer, itemId: String): ConnectionIO[Option[StacItem]] =
    for {
      itemO <- StacItemDao.query.filter(fr"id = ${itemId}").selectOption
      updated <- itemO map { item =>
        val itemLayers = item.getExtensionFields[LayerItemExtension] map { layers =>
          layers.ids.append(layer.id)
        } getOrElse { NonEmptyList.of(layer.id) }
        item.addExtensionFields(LayerItemExtension(itemLayers))
      } traverse { item => StacItemDao.updateStacItemUnsafe(item.id, item) }
    } yield updated

  private def removeOnlyLayer(item: StacItem): ConnectionIO[StacItem] = {
    val patch          = Map("properties" -> Map("layer:ids" -> Option.empty[String]).asJson).asJsonObject
    val itemProperties = item.properties
    val patched        = item.copy(properties = itemProperties.deepMerge(patch.asJsonObject))
    StacItemDao.updateStacItemUnsafe(item.id, patched)
  }

  /** Remove one layer from several layers on an item
    *
    * The type signature here requires you to be able to pass a layer id and a NonEmptyList
    * of other layer ids, so this can only be called with an item that exists in at least two
    * layers.
    */
  private def removeThisLayer(
      item: StacItem,
      layerIdsHead: NonEmptyString,
      layerIdsTail: NonEmptyList[NonEmptyString],
      layerId: NonEmptyString
  ): ConnectionIO[StacItem] = {
    val newLayerIds = if (layerIdsHead == layerId) {
      layerIdsTail
    } else if (layerIdsTail.head == layerId) {
      NonEmptyList(layerIdsHead, layerIdsTail.tail)
    } else {
      NonEmptyList(
        layerIdsHead,
        List(layerIdsTail.head) ++ layerIdsTail.tail.filter(_ != layerId)
      )
    }

    val patched = item.addExtensionFields(LayerItemExtension(newLayerIds))
    StacItemDao.updateStacItemUnsafe(item.id, patched)
  }

  // if this is the only layer the item is in, patch it not to have the layer
  // extension anymore.
  // if it has multiple layers currently, update it to have the other layers, but
  // not this one.
  def removeItem(layer: StacLayer, itemId: String): ConnectionIO[Option[StacLayer]] =
    for {
      itemO <- StacItemDao.query.filter(fr"id = ${itemId}").selectOption
      _ <- itemO flatTraverse { item =>
        item.getExtensionFields[LayerItemExtension] match {
          case Validated.Valid(layerIds) =>
            (layerIds.ids.tail.toNel match {
              case None => removeOnlyLayer(item)
              case Some(tail) =>
                removeThisLayer(item, layerIds.ids.head, tail, layer.id)
            }) map { Option(_) }
          case Validated.Invalid(_) =>
            Option.empty[StacItem].pure[ConnectionIO]
        }
      }
      postRemoval <- itemO traverse { _ => query.filter(fr"id = ${layer.id}").select }
    } yield postRemoval

  def getLayerItem(layer: StacLayer, itemId: String): ConnectionIO[Option[StacItem]] =
    StacItemDao.query.filter(layerQuery(layer)).filter(fr"id=${itemId}").selectOption
}
