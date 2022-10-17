package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacLinkType
import io.circe.Encoder
import io.circe.generic.semiauto._

case class Catalog(
    stacVersion: String,
    stacExtensions: List[String],
    id: String,
    title: Option[String],
    description: String,
    links: List[Link],
    conformsTo: Option[List[String]]
) {

  lazy val dataLinks = links.filter(_.rel == StacLinkType.Data)

  lazy val docLinks =
    links.filter(l => (l.rel == StacLinkType.ServiceDesc || l.rel == StacLinkType.Conformance))
}

object Catalog {

  implicit val catalogEncoder: Encoder[Catalog] = Encoder.forProduct8(
    "type",
    "stac_version",
    "stac_extensions",
    "id",
    "title",
    "description",
    "links",
    "conformsTo"
  )(catalog =>
    (
      "Catalog",
      catalog.stacVersion,
      catalog.stacExtensions,
      catalog.id,
      catalog.title,
      catalog.description,
      catalog.links,
      catalog.conformsTo
    )
  )
  implicit val catalogDecoder = deriveDecoder[Catalog]
}
