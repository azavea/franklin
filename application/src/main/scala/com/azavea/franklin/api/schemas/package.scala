package com.azavea.franklin.api

import cats.implicits._
import com.azavea.franklin.database.{temporalExtentFromString, temporalExtentToString}
import com.azavea.franklin.error.InvalidPatch
import com.azavea.stac4s._
import geotrellis.vector.Geometry
import io.circe.{Encoder, Json}
import io.circe.parser._
import io.circe.syntax._
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.json.circe._
import sttp.tapir.{Codec, DecodeResult, Schema}

import scala.util.Try

import java.util.Base64
import com.azavea.franklin.datamodel.PaginationToken

package object schemas {

  implicit class ToTapirDecodeResult[T](circeResult: Either[io.circe.Error, T]) {

    def toDecodeResult: DecodeResult[T] = {
      circeResult match {
        case Left(err) =>
          DecodeResult.Error(err.getMessage, err)
        case Right(value) =>
          DecodeResult.Value(value)
      }
    }
  }

  val b64Encoder = Base64.getEncoder()
  val b64Decoder = Base64.getDecoder()

  implicit val schemaForTemporalExtent: Schema[TemporalExtent] = Schema(
    schemaForCirceJson.schemaType
  )
  implicit val schemaForGeometry: Schema[Geometry] = Schema(schemaForCirceJson.schemaType)

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

  def encPaginationToken(token: PaginationToken): String = b64Encoder.encodeToString(
    token.asJson.noSpaces.getBytes
  )

  def decPaginationToken(encoded: String): DecodeResult[PaginationToken] = {
    val jsonString: String = new String(b64Decoder.decode(encoded))
    val circeResult = for {
      js      <- parse(jsonString)
      decoded <- js.as[PaginationToken]
    } yield decoded
    circeResult.toDecodeResult
  }

  implicit val codecPaginationToken: Codec.PlainCodec[PaginationToken] =
    Codec.string.mapDecode(decPaginationToken)(encPaginationToken)
}
