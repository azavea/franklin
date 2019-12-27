package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacCollection
import io.circe._
import io.circe.generic.semiauto._

case class CollectionLinks(
    href: String,
    rel: String,
    _type: Option[String],
    hreflang: Option[String],
    title: Option[String],
    length: Option[String]
)

object CollectionLinks {

  implicit val encodeCollectionLinks: Encoder[CollectionLinks] =
    Encoder.forProduct6("href", "rel", "type", "hreflang", "title", "length")(
      cl => (cl.href, cl.rel, cl._type, cl.hreflang, cl.title, cl.length)
    )

  implicit val decodeCollectionLinks: Decoder[CollectionLinks] =
    Decoder.forProduct6("href", "rel", "type", "hreflang", "title", "length")(CollectionLinks.apply)

}

case class CollectionsResponse(
    collections: List[StacCollection],
    links: List[CollectionLinks] = List()
)

object CollectionsResponse {

  implicit val collectionsResponseDecoder: Decoder[CollectionsResponse] =
    deriveDecoder[CollectionsResponse]

  implicit val collectionsResponseEncoder: Encoder[CollectionsResponse] =
    deriveEncoder[CollectionsResponse]
}
