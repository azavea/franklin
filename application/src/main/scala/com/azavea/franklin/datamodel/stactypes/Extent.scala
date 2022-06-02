package com.azavea.franklin.datamodel.stactypes

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import scala.util.Try

case class TimeInterval(start: Option[String], stop: Option[String])

object TimeInterval {

  implicit val intervalEncoder: Encoder[TimeInterval] = Encoder.instance {
    case TimeInterval(start, stop) => List(start.getOrElse(""), stop.getOrElse("")).asJson
  }

  implicit val intervalDecoder: Decoder[TimeInterval] = Decoder[List[String]].emapTry { lst =>
    Try(TimeInterval(lst.lift(0), lst.lift(1)))
  }
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
