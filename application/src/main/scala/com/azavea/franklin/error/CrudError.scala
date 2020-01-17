package com.azavea.franklin.error

import cats.Show
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

sealed abstract class CrudError

case class NotFound(msg: String = "Not found") extends CrudError

object NotFound {
  implicit val encNotFound: Encoder[NotFound] = deriveEncoder
  implicit val decNotFound: Decoder[NotFound] = deriveDecoder
}

case class ValidationError(msg: String) extends CrudError

object ValidationError {
  implicit val encValidationError: Encoder[ValidationError] = deriveEncoder
  implicit val decValidationError: Decoder[ValidationError] = deriveDecoder
}

case class MidAirCollision(msg: String) extends CrudError

object MidAirCollision {
  implicit val encMidAirCollision: Encoder[MidAirCollision] = deriveEncoder
  implicit val decMidAirCollision: Decoder[MidAirCollision] = deriveDecoder
}

case class InvalidPatch(msg: String, patch: Json, error: Error) extends CrudError

object InvalidPatch {

  implicit val encError: Encoder[Error] = new Encoder[Error] {
    def apply(err: Error): Json = Show[Error].show(err).asJson
  }
  implicit val decError: Decoder[Error] = Decoder.decodeString.emap { s =>
    s.takeWhile(_ != ':').toLowerCase match {
      case "parsingfailure" =>
        val baseMessage = s.dropWhile(_ != ':').drop(2)
        Right(ParsingFailure(baseMessage, new Exception(baseMessage)))
      case "decodingfailure" =>
        val baseMessage = s.dropWhile(_ != ':').drop(2)
        // DecodingFailures don't print their list of cursor ops in a way that's
        // easy to recover -- this is definitely not lawful, but I don't _think_
        // we need to decode these error messages ever -- the decoder just has
        // to exist for some tapir typeclass evidence I think
        Right(DecodingFailure(baseMessage, Nil))
    }
  }

  implicit val encInvalidPatch: Encoder[InvalidPatch] = deriveEncoder
  implicit val decInvalidPatch: Decoder[InvalidPatch] = deriveDecoder
}
