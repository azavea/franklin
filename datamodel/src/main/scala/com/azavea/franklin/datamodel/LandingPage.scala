package com.azavea.franklin.datamodel

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto._

case class LandingPage(title: NonEmptyString, description: NonEmptyString, links: List[Link])

object LandingPage {
  implicit val landingPageEncoder = deriveEncoder[LandingPage]
  implicit val landingPageDecoder = deriveDecoder[LandingPage]
}
