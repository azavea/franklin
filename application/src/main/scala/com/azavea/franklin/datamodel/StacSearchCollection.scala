package com.azavea.franklin.datamodel

import com.azavea.stac4s.{StacItem, StacLink}
import io.circe._
import io.circe.syntax._

object StacSearchCollection {

  implicit val stacSearchEncoder = new Encoder[StacSearchCollection] {

    final def apply(a: StacSearchCollection): Json = Json.obj(
      "type"     -> Json.fromString("FeatureCollection"),
      "context"  -> a.context.asJson,
      "features" -> a.features.asJson,
      "links"    -> a.links.asJson
    )
  }

  implicit val stacSearchDecoder = new Decoder[StacSearchCollection] {

    final def apply(c: HCursor): Decoder.Result[StacSearchCollection] =
      for {
        metadata <- c.downField("context").as[Context]
        features <- c.downField("features").as[List[StacItem]]
        links    <- c.downField("links").as[List[StacLink]]
      } yield {
        new StacSearchCollection(metadata, features, links)
      }
  }
}

case class StacSearchCollection(
    context: Context,
    features: List[StacItem],
    links: List[StacLink]
)
