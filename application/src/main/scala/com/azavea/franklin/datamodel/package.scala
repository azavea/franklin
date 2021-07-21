package com.azavea.franklin

import com.azavea.stac4s.{Interval => _, _}
import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.io.json.JsonFeatureCollection
import geotrellis.vector.{Extent, Feature, MultiPolygon}
import io.circe.generic.JsonCodec
import io.circe.refined._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import java.time.Instant

package object datamodel {
  implicit val decoderNonEmptyString: Decoder[NonEmptyString] = refinedDecoder
  implicit val encoderNonEmptyString: Encoder[NonEmptyString] = refinedEncoder

  type Quantile = Int Refined Interval.Closed[W.`0`.T, W.`100`.T]
  object Quantile extends RefinedTypeOps[Quantile, Int]

  implicit class CollectionList(collections: List[StacCollection]) {

    @JsonCodec
    case class Properties(id: String)

    def toGeoJson: String =
      JsonFeatureCollection(collections.map { collection =>
        val geom       = collection.extent.extentGeom
        val properties = Properties(collection.id)
        Feature(geom, properties)
      }).asJson.noSpaces

    def extentMap: Map[String, Extent] = collections.map(c => c.id -> c.extent.extent).toMap

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    def extent: Extent =
      collections
        .flatMap(
          _.extent.spatial.bbox.flatMap(l =>
            l.toExtent match {
              case Left(_)  => None
              case Right(e) => Some(e)
            }
          )
        )
        .reduce(_ combine _)
  }

  implicit class CollectionStacExtent(stacExtent: StacExtent) {

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    def startTime: Option[Instant] =
      stacExtent.temporal.interval.flatMap(_.start) match {
        case l if l.isEmpty => None
        case l              => Some(l.min)
      }

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    def endTime: Option[Instant] =
      stacExtent.temporal.interval.flatMap(_.end) match {
        case l if l.isEmpty => None
        case l              => Some(l.max)
      }

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    def extent: Extent =
      stacExtent.spatial.bbox
        .flatMap { l =>
          l.toExtent match {
            case Left(_)  => None
            case Right(e) => Some(e)
          }
        }
        .reduce(_ combine _)

    def extentGeom: MultiPolygon =
      MultiPolygon(stacExtent.spatial.bbox.flatMap { l =>
        l.toExtent match {
          case Left(_)  => None
          case Right(e) => Some(e.toPolygon)
        }
      })
  }

}
