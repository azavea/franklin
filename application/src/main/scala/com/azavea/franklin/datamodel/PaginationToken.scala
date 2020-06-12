package com.azavea.franklin.datamodel

import com.azavea.stac4s.meta._
import eu.timepit.refined.types.numeric.PosInt
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.refined._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import sttp.tapir.DecodeResult

import java.time.Instant
import java.util.Base64

final case class PaginationToken(
    timestampAtLeast: Instant,
    serialIdGreaterThan: PosInt
)

object PaginationToken {

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

  implicit val dec: Decoder[PaginationToken] = deriveDecoder
  implicit val enc: Encoder[PaginationToken] = deriveEncoder

  val b64Encoder = Base64.getEncoder()
  val b64Decoder = Base64.getDecoder()

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

}
