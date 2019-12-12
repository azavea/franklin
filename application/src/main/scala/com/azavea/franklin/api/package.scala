package com.azavea.franklin.api

import com.azavea.franklin.database.{temporalExtentFromString, temporalExtentToString}
import geotrellis.server.stac.{Bbox, TemporalExtent, ThreeDimBbox, TwoDimBbox}
import geotrellis.vector.Geometry
import tapir.Codec.PlainCodec
import tapir.{Codec, DecodeResult, SchemaFor}
import tapir.json.circe.schemaForCirceJson

import scala.util.Try

package object schemas {

  implicit val schemaForTemporalExtent: SchemaFor[TemporalExtent] = SchemaFor(
    schemaForCirceJson.schema
  )
  implicit val schemaForGeometry: SchemaFor[Geometry] = SchemaFor(schemaForCirceJson.schema)

  def decode(s: String): DecodeResult[TemporalExtent] = {
    temporalExtentFromString(s) match {
      case Left(e)  => DecodeResult.Mismatch("valid timestamp", e)
      case Right(v) => DecodeResult.Value(v)
    }
  }

  // or, using the type alias for codecs in the TextPlain format and String as the raw value:
  implicit val teCodec: PlainCodec[TemporalExtent] = Codec.stringPlainCodecUtf8
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
    Codec.stringPlainCodecUtf8.mapDecode(bboxFromString)(bboxToString)

  def commaSeparatedStrings(s: String): DecodeResult[List[String]] =
    DecodeResult.Value(s.split(",").toList)
  def listToCSV(collections: List[String]): String = collections.mkString(",")

  implicit val csvListCodec: PlainCodec[List[String]] =
    Codec.stringPlainCodecUtf8.mapDecode(commaSeparatedStrings)(listToCSV)

}
