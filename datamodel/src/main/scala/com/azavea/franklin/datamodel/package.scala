package com.azavea.franklin

import eu.timepit.refined.api.RefType
import io.circe.{Decoder, Encoder}
import io.circe.refined._
import tapir._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString

package object datamodel {
  implicit val decoderNonEmptyString: Decoder[NonEmptyString] = refinedDecoder
  implicit val encoderNonEmptyString: Encoder[NonEmptyString] = refinedEncoder

  private def decJobStatus(s: String): DecodeResult[NonEmptyString] =
    RefType.applyRef[NonEmptyString](s) match {
      case Right(nonEmptyString) => DecodeResult.Value(nonEmptyString)
      case Left(err)             => DecodeResult.Error(err, new Exception(err))
    }

  implicit val jobStatusCodec: Codec[NonEmptyString, MediaType.TextPlain, String] =
    Codec.stringPlainCodecUtf8.mapDecode(decJobStatus)(_.value)
}
