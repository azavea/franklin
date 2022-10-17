package com.azavea.franklin.datamodel

import cats.syntax.all._
import com.azavea.stac4s.meta.ForeignImplicits._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import scala.util.Try

import java.time.{Instant, OffsetDateTime}

case class TimeInterval(start: Option[Instant], end: Option[Instant]) {

  override def toString: String =
    this match {
      case TimeInterval(Some(start), Some(end)) if start != end =>
        s"${start.toString}/${end.toString}"
      case TimeInterval(Some(start), Some(end)) if start == end => s"${start.toString}"
      case TimeInterval(Some(start), None)                      => s"${start.toString}/.."
      case TimeInterval(None, Some(end))                        => s"../${end.toString}"
      case _                                                    => "../.."
    }
}

object TimeInterval {

  def stringToInstant: String => Either[Throwable, Instant] =
    (s: String) => Either.catchNonFatal(OffsetDateTime.parse(s, RFC3339formatter).toInstant)

  def fromString(str: String): Either[String, TimeInterval] =
    str.split("/").toList match {
      case ".." :: ".." :: _ | "" :: "" :: _ => Right(TimeInterval(None, None))
      case (".." | "") :: endString :: _ =>
        stringToInstant(endString).bimap(
          _ => s"Could not decode instant: $str",
          end => TimeInterval(None, end.some)
        )
      case startString :: ".." :: _ =>
        stringToInstant(startString).bimap(
          _ => s"Could not decode instant: $str",
          start => TimeInterval(start.some, None)
        )
      case startString :: endString :: _ =>
        val parsedStart: Either[Throwable, Instant] = stringToInstant(startString)
        val parsedEnd: Either[Throwable, Instant]   = stringToInstant(endString)
        (parsedStart, parsedEnd).tupled.bimap(_ => s"Could not decode instant: $str", {
          case (start: Instant, end: Instant) => TimeInterval(start.some, end.some)
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
          start => TimeInterval(start.some, None)
        )
      case _ =>
        stringToInstant(str).bimap(
          _ => s"Could not decode instant: $str",
          t => TimeInterval(t.some, t.some)
        )
    }

  implicit val intervalDecoder: Decoder[TimeInterval] = { c: HCursor =>
    c.value.as[List[Option[Instant]]] flatMap {
      case h :: t :: Nil => Right(TimeInterval(h, t))
      case _             => Left(DecodingFailure("Temporal extents must have exactly two elements", c.history))
    }
  }

  implicit val intervalEncoder: Encoder[TimeInterval] =
    Encoder[List[Option[Instant]]].contramap(ext => List(ext.start, ext.end))
}

case class SpatialExtent(bbox: List[List[Double]])

object SpatialExtent {
  implicit val decoder: Decoder[SpatialExtent] = deriveDecoder
  implicit val encoder: Encoder[SpatialExtent] = deriveEncoder
}

case class TemporalExtent(interval: List[TimeInterval])

object TemporalExtent {
  implicit val decoder: Decoder[TemporalExtent] = deriveDecoder
  implicit val encoder: Encoder[TemporalExtent] = deriveEncoder
}

case class Extent(spatial: SpatialExtent, temporal: TemporalExtent)

object Extent {
  implicit val extentDecoder: Decoder[Extent] = deriveDecoder
  implicit val extentEncoder: Encoder[Extent] = deriveEncoder
}
