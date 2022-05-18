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
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.Geometry
import geotrellis.vector.{io => _, _}
import io.circe.parser.{decode => circeDecode}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.{Codec, DecodeResult, Schema}

import scala.util.Try

package object schemas {

  implicit val schemaStacCollection: Schema[StacCollection] = Schema(schemaForCirceJson.schemaType)

  implicit val schemaForTemporalExtent: Schema[TemporalExtent] = Schema(
    schemaForCirceJson.schemaType
  )
  implicit val schemaForGeometry: Schema[Geometry] = Schema(schemaForCirceJson.schemaType)

  def decodeGeom(s: String): DecodeResult[Geometry] = circeDecode[Geometry](s) match {
    case Right(v) => DecodeResult.Value(v)
    case Left(f)  => DecodeResult.Error(s, f)
  }
  def encodeGeom(geom: Geometry): String = geom.asJson.noSpaces

  implicit val GeomCodec: Codec[String, Geometry, TextPlain] =
    Codec.string.mapDecode(decodeGeom)(encodeGeom)

  def decodeJson(s: String): DecodeResult[Json] = circeDecode[Json](s) match {
    case Right(v) => DecodeResult.Value(v)
    case Left(f)  => DecodeResult.Error(s, f)
  }
  def encodeJson(json: Json): String = json.noSpaces

  implicit val JsonCodec: Codec[String, Json, TextPlain] =
    Codec.string.mapDecode(decodeJson)(encodeJson)

  implicit val schemaForStacItem: Schema[StacItem]         = Schema(schemaForCirceJson.schemaType)
  implicit val schemaForInvalidPatch: Schema[InvalidPatch] = Schema(schemaForCirceJson.schemaType)

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

  implicit val schemaForStacLink: Schema[StacLinkType] =
    Schema.schemaForString.map(s => s.asJson.as[StacLinkType].toOption)(_.repr)

  implicit val codecIfMatchMode: Codec.PlainCodec[IfMatchMode] =
    Codec.string.mapDecode(s => DecodeResult.Value(IfMatchMode.fromString(s)))(_.toString)
}
