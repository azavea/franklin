package com.azavea.franklin

import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.refined._
import io.circe._
import com.azavea.stac4s._
import io.circe.syntax._
import geotrellis.vector.MultiPolygon

import geotrellis.vector.Feature
import geotrellis.vector.io.json.JsonFeatureCollection
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.Polygon

import io.circe.generic.JsonCodec

package object datamodel {

  implicit val decoderNonEmptyString: Decoder[NonEmptyString] = refinedDecoder
  implicit val encoderNonEmptyString: Encoder[NonEmptyString] = refinedEncoder

  type Quantile = Int Refined Interval.Closed[W.`0`.T, W.`100`.T]
  object Quantile extends RefinedTypeOps[Quantile, Int]

  implicit class CollectionList(collections: List[StacCollection]) {

    @JsonCodec
    case class Properties(id: String)

    def toGeoJson = {
      JsonFeatureCollection(collections.map { collection =>
        val geom = collection.extent.extentGeom
        val properties = Properties(collection.id)
        Feature(geom, properties)
      }).asJson.noSpaces
    }

    def extent = {
      collections.flatMap(_.extent.spatial.bbox.flatMap(l => l.toExtent match {
        case Left(_) => None
        case Right(e) => Some(e)
      })).reduce( _ combine _ )
    }
  }

  implicit class CollectionStacExtent(stacExtent: StacExtent) {

    def startTime = {
      stacExtent.temporal.interval.flatMap(_.value.headOption).flatten match {
        case l if l.isEmpty => None
        case l => Some(l.min)
      }
    }
    def endTime = {
      stacExtent.temporal.interval.flatMap(_.value.lift(1)).flatten match {
        case l if l.isEmpty => None
        case l => Some(l.max)
      }
    }

    def extent = {
      (stacExtent.spatial.bbox.flatMap(l => l.toExtent match {
        case Left(_) => None
        case Right(e) => Some(e)
      })).reduce(_ combine _)
    }

    def extentGeom = {
      MultiPolygon(stacExtent.spatial.bbox.flatMap(l => l.toExtent match {
        case Left(_) => None
        case Right(e) => Some(e.toPolygon)
      }))
    }
  }
>>>>>>> WIP
}
