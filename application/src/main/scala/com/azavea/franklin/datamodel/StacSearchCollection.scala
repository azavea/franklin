package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacItem
import io.circe._
import io.circe.syntax._

case class StacSearchCollection(
    context: Option[Context],
    features: List[StacItem],
    links: List[Link] = List(),
    next: Option[String] = None,
    prev: Option[String] = None
)

object StacSearchCollection {

  implicit val stacSearchEncoder: Encoder[StacSearchCollection] = Encoder.forProduct5(
    "type",
    "context",
    "features",
    "links",
    "stac_version"
  )(searchResults =>
    (
      "FeatureCollection",
      searchResults.context,
      searchResults.features,
      searchResults.links,
      "1.0.0"
    )
  )

  implicit val stacSearchDecoder = new Decoder[StacSearchCollection] {

    final def apply(c: HCursor): Decoder.Result[StacSearchCollection] =
      for {
        context  <- c.downField("context").as[Option[Context]]
        features <- c.downField("features").as[List[StacItem]]
        links    <- c.downField("links").as[Option[List[Link]]]
        next     <- c.downField("next").as[Option[String]]
        prev     <- c.downField("prev").as[Option[String]]
      } yield {
        new StacSearchCollection(context, features, links.getOrElse(List()), next, prev)
      }
  }
}
