package com.azavea.franklin

import cats.data.NonEmptyList
import cats.syntax.all._
import com.azavea.franklin.datamodel.BulkExtent
import com.azavea.stac4s.meta.ForeignImplicits._
import com.azavea.stac4s.{Bbox, StacItem, TemporalExtent, ThreeDimBbox, TwoDimBbox}
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

  implicit val instantMeta: Meta[Instant]   = Meta[Timestamp].imap(_.toInstant)(Timestamp.from)
  implicit val instantRead: Read[Instant]   = Read[Timestamp].imap(_.toInstant)(Timestamp.from)
  implicit val instantWrite: Write[Instant] = Write[Timestamp].imap(_.toInstant)(Timestamp.from)

  def stringToInstant: String => Either[Throwable, Instant] =
    (s: String) => Either.catchNonFatal(OffsetDateTime.parse(s, RFC3339formatter).toInstant)

  def temporalExtentToString(te: TemporalExtent): String = {
    te match {
      case TemporalExtent(Some(start), Some(end)) if start != end =>
        s"${start.toString}/${end.toString}"
      case TemporalExtent(Some(start), Some(end)) if start == end => s"${start.toString}"
      case TemporalExtent(Some(start), None)                      => s"${start.toString}/.."
      case TemporalExtent(None, Some(end))                        => s"../${end.toString}"
      case _                                                      => "../.."
    }
  }

  def temporalExtentFromString(str: String): Either[String, TemporalExtent] = {
    str.split("/").toList match {
      case ".." :: ".." :: _ | "" :: "" :: _ => Right(TemporalExtent(None, None))
      case (".." | "") :: endString :: _ =>
        stringToInstant(endString).bimap(
          _ => s"Could not decode instant: $str",
          end => TemporalExtent(None, end)
        )
      case startString :: ".." :: _ =>
        stringToInstant(startString).bimap(
          _ => s"Could not decode instant: $str",
          start => TemporalExtent(start, None)
        )
      case startString :: endString :: _ =>
        val parsedStart: Either[Throwable, Instant] = stringToInstant(startString)
        val parsedEnd: Either[Throwable, Instant]   = stringToInstant(endString)
        (parsedStart, parsedEnd).tupled.bimap(_ => s"Could not decode instant: $str", {
          case (start: Instant, end: Instant) => TemporalExtent(start, end)
        })
      // the behavior of split is that if the last character of the string is the splitting
      // character, you get everything before it, then nothing. this is different from if it's
      // the first character, in which case you get an empty string and then the rest.
      // however, we need to differentiate cases where splitting on "/" only gets us a single element
      // list, because for us, foo/ and foo indicate different things --
      // with the trailing slash, it's an open ended interval starting at foo. without the trailing slash,
      // it's the exact point in time represented by foo.
      case startString :: Nil if str.endsWith("/") =>
        stringToInstant(startString).bimap(
          _ => s"Could not decode instant: $str",
          start => TemporalExtent(start, None)
        )
      case _ =>
        stringToInstant(str).bimap(
          _ => s"Could not decode instant: $str",
          t => TemporalExtent(t, t)
        )
    }
  }

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

  implicit val encoderTemporalExtent: Encoder[TemporalExtent] =
    Encoder.encodeString.contramap[TemporalExtent] { extent => temporalExtentToString(extent) }

  implicit val decoderTemporalExtent: Decoder[TemporalExtent] = Decoder.decodeString.emap { str =>
    temporalExtentFromString(str)
  }

  implicit class bboxToTwoDim(bbox: Bbox) {

    def toTwoDim: TwoDimBbox = bbox match {
      case ThreeDimBbox(xmin, ymin, _, xmax, ymax, _) =>
        TwoDimBbox(xmin, ymin, xmax, ymax)

      case twoD: TwoDimBbox => twoD
    }

  }
}
