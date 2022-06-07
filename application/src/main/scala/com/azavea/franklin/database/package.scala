package com.azavea.franklin

import cats.data.NonEmptyList
import cats.syntax.all._
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.meta.ForeignImplicits._
import com.azavea.stac4s.{Bbox, StacItem, ThreeDimBbox, TwoDimBbox}
import doobie.implicits.javasql._
import doobie.util.meta.Meta
import doobie.util.{Read, Write}
import geotrellis.vector.Extent
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Error => CirceError}

import java.sql.Timestamp
import java.time.{Instant, OffsetDateTime}

package object database extends CirceJsonbMeta with GeotrellisWktMeta {

  implicit val instantMeta: Meta[Instant] = Meta[Timestamp].imap(_.toInstant)(Timestamp.from)

  def getItemsBulkExtent(items: NonEmptyList[StacItem]): BulkExtent =
    items.foldLeft(BulkExtent(None, None, items.head.bbox))({
      case (BulkExtent(start, end, bbox), item) => {
        val itemDt  = item.properties.asJson.hcursor.downField("datetime").as[Instant].toOption
        val newBbox = bbox.union(item.bbox)
        val newEndpoints = itemDt flatMap { newDt =>
          (start map { dt =>
            if (dt.isBefore(newDt)) { dt }
            else newDt
          } orElse Some(newDt), end map { dt =>
            if (dt.isAfter(newDt)) dt else newDt
          } orElse Some(newDt)).tupled
        }

        newEndpoints match {
          case Some((newStart, newEnd)) =>
            BulkExtent(Some(newStart), Some(newEnd), newBbox.toTwoDim)
          case None => BulkExtent(start, end, newBbox.toTwoDim)
        }

      }
    })

  implicit val encoderTimeInterval: Encoder[TimeInterval] =
    Encoder.encodeString.contramap[TimeInterval] { interval => interval.toString }

  implicit val decoderTimeInterval: Decoder[TimeInterval] = Decoder.decodeString.emap { str =>
    TimeInterval.fromString(str)
  }

  implicit class bboxToTwoDim(bbox: Bbox) {

    def toTwoDim: TwoDimBbox = bbox match {
      case ThreeDimBbox(xmin, ymin, _, xmax, ymax, _) =>
        TwoDimBbox(xmin, ymin, xmax, ymax)

      case twoD: TwoDimBbox => twoD
    }

  }
}
