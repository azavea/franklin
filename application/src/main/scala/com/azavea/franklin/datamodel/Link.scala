package com.azavea.franklin.datamodel

import cats.syntax.either._
import com.azavea.stac4s.{StacLinkType, StacMediaType}
import io.circe.{Encoder, Decoder, Json}
import io.circe.generic.semiauto._
import org.http4s.{Method, ParseResult, ParseFailure}
import sttp.tapir.Schema
import sttp.tapir.codec.refined._

case class Link(
  href: String,
  rel: StacLinkType,
  _type: Option[StacMediaType],
  title: Option[String] = None,
  method: Option[Method] = None,
  body: Option[Json] = None
)

object Link {

  implicit val encStacLink: Encoder[Link] = Encoder.forProduct6(
    "href",
    "rel",
    "type",
    "title",
    "method",
    "body"
  )(link => (link.href, link.rel, link._type, link.title, link.method.map(_.name), link.body))

  implicit val decodeMethod: Decoder[Method] = Decoder.decodeString.emap { str =>
    val parsed: Either[ParseFailure, Method] = Method.fromString(str)
    parsed.leftMap(_ => "Failed to parse method string")
  }

  implicit val decoderLink = deriveDecoder[Link]
}
