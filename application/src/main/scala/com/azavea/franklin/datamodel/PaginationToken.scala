package com.azavea.franklin.datamodel

import cats.syntax.either._
import com.azavea.stac4s.meta._
import eu.timepit.refined.types.numeric.PosInt
import io.circe.Error
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

  val defaultDecoder: Decoder[PaginationToken] = deriveDecoder
  val defaultEncoder: Encoder[PaginationToken] = deriveEncoder

  val b64Encoder: Base64.Encoder = Base64.getEncoder
  val b64Decoder: Base64.Decoder = Base64.getDecoder

  def encPaginationToken(token: PaginationToken): String = b64Encoder.encodeToString(
    token.asJson(defaultEncoder).noSpaces.getBytes
  )

  def decPaginationTokenEither(encoded: String): Either[Error, PaginationToken] = {
    val jsonString = new String(b64Decoder.decode(encoded))
    for {
      js      <- parse(jsonString)
      decoded <- js.as[PaginationToken](defaultDecoder)
    } yield decoded
  }

  def decPaginationToken(encoded: String): DecodeResult[PaginationToken] =
    decPaginationTokenEither(encoded).toDecodeResult

  implicit val paginationTokenDecoder: Decoder[PaginationToken] =
    Decoder.decodeString.emap(str => decPaginationTokenEither(str).leftMap(_.getMessage))

  implicit val paginationTokenEncoder: Encoder[PaginationToken] = { encPaginationToken(_).asJson }

}
