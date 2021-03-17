package com.azavea.franklin

import cats.syntax.all._
import com.azavea.franklin.datamodel.BulkExtent
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.types.TemporalExtent
import doobie.implicits.javasql._
import doobie.util.meta.Meta
import doobie.util.{Read, Write}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import java.sql.Timestamp
import java.time.Instant

package object database extends CirceJsonbMeta with GeotrellisWktMeta with Filterables {

  implicit val instantMeta: Meta[Instant]   = Meta[Timestamp].imap(_.toInstant)(Timestamp.from)
  implicit val instantRead: Read[Instant]   = Read[Timestamp].imap(_.toInstant)(Timestamp.from)
  implicit val instantWrite: Write[Instant] = Write[Timestamp].imap(_.toInstant)(Timestamp.from)

  def stringToInstant: String => Either[Throwable, Instant] =
    (s: String) => Either.catchNonFatal(Instant.parse(s))

  def temporalExtentToString(te: TemporalExtent): String = {
    te.value match {
      case Some(start) :: Some(end) :: _ if start != end => s"${start.toString}/${end.toString}"
      case Some(start) :: Some(end) :: _ if start == end => s"${start.toString}"
      case Some(start) :: None :: _                      => s"${start.toString}/.."
      case None :: Some(end) :: _                        => s"../${end.toString}"
    }
  }

  def temporalExtentFromString(str: String): Either[String, TemporalExtent] = {
    str.split("/").toList match {
      case ".." :: endString :: _ =>
        val parsedEnd: Either[Throwable, Instant] = stringToInstant(endString)
        parsedEnd match {
          case Left(_)             => Left(s"Could not decode instant: $str")
          case Right(end: Instant) => Right(TemporalExtent(None, end))
        }
      case startString :: ".." :: _ =>
        val parsedStart: Either[Throwable, Instant] = stringToInstant(startString)
        parsedStart match {
          case Left(_)               => Left(s"Could not decode instant: $str")
          case Right(start: Instant) => Right(TemporalExtent(start, None))
        }
      case startString :: endString :: _ =>
        val parsedStart: Either[Throwable, Instant] = stringToInstant(startString)
        val parsedEnd: Either[Throwable, Instant]   = stringToInstant(endString)
        (parsedStart, parsedEnd).tupled match {
          case Left(_)                               => Left(s"Could not decode instant: $str")
          case Right((start: Instant, end: Instant)) => Right(TemporalExtent(start, end))
        }
      case _ =>
        Either.catchNonFatal(Instant.parse(str)) match {
          case Left(_)           => Left(s"Could not decode instant: $str")
          case Right(t: Instant) => Right(TemporalExtent(t, t))
        }
    }
  }

  def getItemsBulkExtent(items: List[StacItem]) =
    items.foldLeft(BulkExtent(None, None, None))({
      case (BulkExtent(start, end, bbox), item) => {
        val itemDt   = item.properties.asJson.hcursor.downField("datetime").as[Instant].toOption
        val itemBbox = item.bbox
        val newBbox  = bbox.map(box => box.union(itemBbox)) getOrElse itemBbox
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
            BulkExtent(Some(newStart), Some(newEnd), Some(newBbox))
          case None => BulkExtent(start, end, Some(newBbox))
        }

      }
    })

  implicit val encoderTemporalExtent: Encoder[TemporalExtent] =
    Encoder.encodeString.contramap[TemporalExtent] { extent => temporalExtentToString(extent) }

  implicit val decoderTemporalExtent: Decoder[TemporalExtent] = Decoder.decodeString.emap { str =>
    temporalExtentFromString(str)
  }
}
