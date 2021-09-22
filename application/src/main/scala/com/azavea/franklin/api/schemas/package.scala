package com.azavea.franklin.api

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.syntax.invariant._
import cats.syntax.traverse._
import com.azavea.franklin.database.{temporalExtentFromString, temporalExtentToString}
import com.azavea.franklin.datamodel.IfMatchMode
import com.azavea.franklin.datamodel.PaginationToken
import com.azavea.franklin.error.InvalidPatch
import com.azavea.stac4s._
import com.azavea.stac4s.types._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.Geometry
import io.circe.syntax._
import io.circe.{Encoder, Json}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.codec.enumeratum._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.{Codec, DecodeResult, Schema, SchemaType}

import scala.util.Try

package object schemas {

  implicit val schemaStacLicense: Schema[StacLicense] = Schema.derived

  implicit val schemaSpatialExtent: Schema[SpatialExtent] = Schema.derived

  implicit val schemaTemporalExtent: Schema[List[TemporalExtent]] = Schema.derived

  implicit val schemaInterval: Schema[Interval] = Schema.derived

  implicit val schemaStacExtent: Schema[StacExtent] = Schema.derived

  implicit val schemaSummaryValue: Schema[SummaryValue] = Schema.derived

  // We can fill in a product schema without any fields as a placeholder, which is
  // no worse than the schema for circe json that we used to have
  implicit val schemaSummaries: Schema[Map[NonEmptyString, SummaryValue]] = Schema.apply(
    SchemaType.SProduct[Map[NonEmptyString, SummaryValue]](Nil),
    Some(Schema.SName("summaries"))
  )

  implicit val schemaForStacLink: Schema[StacLinkType] = Schema.derived

  implicit val schemaStacCollection: Schema[StacCollection] = Schema.derived

  implicit val schemaForTemporalExtent: Schema[TemporalExtent] = Schema.derived

  // We can fill in a product schema without any fields as a placeholder, which is
  // no worse than the schema for circe json that we used to have
  implicit val schemaForGeometry: Schema[Geometry] = Schema(
    SchemaType.SProduct[Geometry](Nil),
    Some(Schema.SName("geometry"))
  )

  implicit val schemaForBbox: Schema[TwoDimBbox] = Schema.derived

  // We can fill in a product schema without any fields as a placeholder, which is
  // no worse than the schema for circe json that we used to have
  implicit val schemaForItemDatetime: Schema[ItemDatetime] = Schema(
    SchemaType.SProduct[ItemDatetime](Nil),
    Some(Schema.SName("datetime"))
  )

  implicit val schemaForItemProperties: Schema[ItemProperties] = Schema.derived

  implicit val schemaForStacItem: Schema[StacItem]         = Schema.derived
  implicit val schemaForInvalidPatch: Schema[InvalidPatch] = Schema.derived

  def decode(s: String): DecodeResult[TemporalExtent] = {
    temporalExtentFromString(s) match {
      case Left(e)  => DecodeResult.Mismatch("valid timestamp", e)
      case Right(v) => DecodeResult.Value(v)
    }
  }

  // or, using the type alias for codecs in the TextPlain format and String as the raw value:
  implicit val teCodec: PlainCodec[TemporalExtent] = Codec.string
    .mapDecode(decode)(temporalExtentToString)

  def bboxFromString(s: String): DecodeResult[Bbox] = {
    val numberList = s.split(",").map(d => Try(d.toDouble).toEither)
    val failed     = numberList.find(_.isLeft)
    failed match {
      case Some(Left(error)) => DecodeResult.Error("invalid bbox", error)
      case _ => {
        numberList.flatMap(_.toOption).toList match {
          case xmin :: ymin :: zmin :: xmax :: ymax :: zmax :: _ =>
            DecodeResult.Value(ThreeDimBbox(xmin, ymin, zmin, xmax, ymax, zmax))
          case xmin :: ymin :: xmax :: ymax :: _ =>
            DecodeResult.Value(TwoDimBbox(xmin, ymin, xmax, ymax))
          case _ => DecodeResult.Mismatch("must be 4 or 6 numbers separated by commas", s)
        }
      }
    }
  }

  def bboxToString(bbox: Bbox): String = bbox match {
    case ThreeDimBbox(xmin, ymin, zmin, xmax, ymax, zmax) => s"$xmin,$ymin,$zmin,$xmax,$ymax,$zmax"
    case TwoDimBbox(xmin, ymin, xmax, ymax)               => s"$xmin,$ymin,$xmax,$ymax"
  }

  implicit val bboxCodec: PlainCodec[Bbox] =
    Codec.string.mapDecode(bboxFromString)(bboxToString)

  def commaSeparatedStrings(s: String): DecodeResult[List[String]] =
    DecodeResult.Value(s.split(",").toList)
  def listToCSV(collections: List[String]): String = collections.mkString(",")

  implicit val csvListCodec: PlainCodec[List[String]] =
    Codec.string.mapDecode(commaSeparatedStrings)(listToCSV)

  def decStacItem(json: Json): DecodeResult[StacItem] = json.as[StacItem] match {
    case Left(err) => DecodeResult.Error(err.getMessage, err)
    case Right(v)  => DecodeResult.Value(v)
  }
  def encStacItem(stacItem: StacItem): Json = Encoder[StacItem].apply(stacItem)

  val jsonCodec: Codec.JsonCodec[Json] = implicitly[Codec.JsonCodec[Json]]

  implicit val codecStacItem: Codec.JsonCodec[StacItem] =
    jsonCodec.mapDecode(decStacItem)(encStacItem)

  implicit val codecPaginationToken: Codec.PlainCodec[PaginationToken] =
    Codec.string.mapDecode(PaginationToken.decPaginationToken)(PaginationToken.encPaginationToken)

  implicit val codecIfMatchMode: Codec.PlainCodec[IfMatchMode] =
    Codec.string.mapDecode(s => DecodeResult.Value(IfMatchMode.fromString(s)))(_.toString)
}
