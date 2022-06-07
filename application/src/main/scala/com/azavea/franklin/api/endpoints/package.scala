package com.azavea.franklin.api

import cats.data.{NonEmptyList, Validated}
import com.azavea.franklin.datamodel._
import io.circe.syntax._
import io.circe.{CursorOp, Decoder, DecodingFailure, Encoder, Json, ParsingFailure}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.SchemaType._
import sttp.tapir._

package object endpoints {

  def prependApiPath(prefix: String, basePath: EndpointInput[Unit]): EndpointInput[Unit] = {
    val strippedBase = prefix.stripPrefix("/").stripSuffix("/")
    val pathComponents: NonEmptyList[String] =
      NonEmptyList.fromListUnsafe(strippedBase.split("/").toList)
    val baseInput =
      pathComponents.tail.foldLeft(pathComponents.head: EndpointInput[Unit])((x, y) => x / y)
    baseInput / basePath
  }

  def baseFor(prefix: Option[String], basePath: EndpointInput[Unit]) =
    prefix.fold(basePath)(prefix => prependApiPath(prefix, basePath))

  private def circeErrorToFailure(
      failure: io.circe.Error
  ): DecodeResult.Error.JsonError = failure match {
    case DecodingFailure(err, hist) =>
      val path   = CursorOp.opsToPath(hist)
      val fields = path.split("\\.").toList.filter(_.nonEmpty).map(FieldName.apply)
      DecodeResult.Error.JsonError(err, fields)
    case ParsingFailure(message, _) =>
      DecodeResult.Error.JsonError(message, path = List.empty)
  }

  private def errsToMessage(
      errs: NonEmptyList[io.circe.Error]
  ): Exception = {
    val errMessage = Map[String, Json](
      "parseFailures" -> errs
        .collect({
          case ParsingFailure(_, underlying) =>
            s"Failed to parse input as JSON: ${underlying.getMessage}"
        })
        .asJson,
      "decodingFailures" -> errs
        .collect({
          case DecodingFailure("Attempt to decode value on failed cursor", history) =>
            s"${CursorOp.opsToPath(history)} was missing but required"
          case DecodingFailure(s, history) =>
            List(
              s"${CursorOp.opsToPath(history)} had the wrong type.",
              s"The error was: $s.",
              "If the error is a plain type like 'String', the value must be that type, for example a String instead of an Int.",
              "If the error says 'Predicate failed: ___', then the stated predicate must succeed for the value to be valid."
            ).mkString(" ")

        })
        .asJson
    )
    new Exception(errMessage.asJson.spaces2)
  }

// def decodeTemporalExtent(s: String): DecodeResult[TemporalExtent] = {
//   val intervals: List[Option[TimeInterval]] =
//     s.split(",").map(TimeInterval.fromString(_).toOption).toList
//   if (intervals.contains(None)) {
//     DecodeResult.Missing
//   } else {
//     DecodeResult.Value(TemporalExtent(intervals.flatten))
//   }
// }

// def encodeTemporalExtent(id: TemporalExtent): String = id.toString

// implicit val temporalExtentCodec: Codec[String, TemporalExtent, TextPlain] =
//   Codec.string.mapDecode(decodeTemporalExtent)(encodeTemporalExtent)

  private def circeCodec[T: Encoder: Decoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.circe.parser.decodeAccumulating[T](s) match {
        case Validated.Invalid(errs) =>
          DecodeResult.Error(s, DecodeResult.Error.JsonDecodeException(errs.toList map {
            circeErrorToFailure
          }, errsToMessage(errs)))
        case Validated.Valid(v) => DecodeResult.Value(v)
      }
    } { t => t.asJson.noSpaces }

  def accumulatingJsonBody[T: Encoder: Decoder: Schema]: EndpointIO.Body[String, T] =
    anyFromUtf8StringBody(circeCodec[T])
}
