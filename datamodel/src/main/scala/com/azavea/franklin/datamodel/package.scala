package com.azavea.franklin

import eu.timepit.refined.api.{RefType, Refined, RefinedTypeOps}
import eu.timepit.refined.collection.NonEmpty
import io.circe.{Decoder, Encoder}
import io.circe.refined._
import tapir._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._

package object datamodel {
  type NonEmptyString = String Refined NonEmpty
  object NonEmptyString extends RefinedTypeOps[NonEmptyString, String]

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
