package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacLinkType
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto._

case class LandingPage(title: NonEmptyString, description: NonEmptyString, links: List[Link]) {

  lazy val dataLinks = links.filter(_.rel == StacLinkType.Data)

  lazy val docLinks =
    links.filter(l => (l.rel == StacLinkType.ServiceDesc || l.rel == StacLinkType.Conformance))
}

object LandingPage {
  implicit val landingPageEncoder = deriveEncoder[LandingPage]
  implicit val landingPageDecoder = deriveDecoder[LandingPage]
}
