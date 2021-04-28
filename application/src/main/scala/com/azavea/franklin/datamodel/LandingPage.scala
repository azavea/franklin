package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacLinkType
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Encoder
import io.circe.generic.semiauto._
import com.azavea.stac4s.StacLink

case class LandingPage(
    stacVersion: String,
    stacExtensions: List[String],
    title: Option[String],
    id: NonEmptyString,
    description: NonEmptyString,
    links: List[Link],
    conformsTo: List[NonEmptyString]
) {

  lazy val dataLinks = links.filter(_.rel == StacLinkType.Data)

  lazy val docLinks =
    links.filter(l => (l.rel == StacLinkType.ServiceDesc || l.rel == StacLinkType.Conformance))
}

object LandingPage {

  implicit val landingPageEncoder: Encoder[LandingPage] = Encoder.forProduct8(
    "type",
    "stac_version",
    "stac_extensions",
    "title",
    "id",
    "description",
    "links",
    "conformsTo"
  )(landingPage =>
    (
      "Catalog",
      landingPage.stacVersion,
      landingPage.stacExtensions,
      landingPage.title,
      landingPage.id,
      landingPage.description,
      landingPage.links,
      landingPage.conformsTo
    )
  )
  implicit val landingPageDecoder = deriveDecoder[LandingPage]
}
