package com.azavea.franklin.services

import cats._
import cats.effect._
import cats.implicits._
import com.azavea.franklin.datamodel._
import com.azavea.franklin.endpoints.LandingPageEndpoints
import geotrellis.server.stac.Self
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._
import geotrellis.server.stac._
import eu.timepit.refined.auto._

class LandingPageService[F[_]: Sync](implicit contextShift: ContextShift[F]) extends Http4sDsl[F] {

  val links = List(
    Link(
      "http://localhost:9090",
      Self,
      Some(`application/json`),
      Some("Franklin Powered Catalog")
    ),
    Link(
      "http://localhost:9090/api/docs.yaml",
      VendorLinkType("service-desc"),
      Some(`application/json`),
      Some("Franklin Powered Catalog")
    ),
    Link(
      "http://localhost:9090/conformance",
      VendorLinkType("conformance"),
      Some(`application/json`),
      Some("Franklin Powered Catalog")
    ),
    Link(
      "http://localhost:9090/collections",
      VendorLinkType("data"),
      Some(`application/json`),
      Some("Franklin Powered Catalog")
    ),
    Link(
      "http://localhost:9090/search",
      VendorLinkType("search"),
      Some(`application/geo+json`),
      Some("Franklin Powered Catalog")
    )
  )

  def landingPage(): F[Either[Unit, Json]] = {
    Applicative[F].pure {
      val title: NonEmptyString = "Franklin Powered OGC API - Features and STAC web service"
      val description: NonEmptyString =
        "Web service powered by [Franklin](https://github.com/azavea/franklin)"
      Right(LandingPage(title, description, links).asJson)
    }
  }

  def conformancePage(): F[Either[Unit, Json]] = {
    Applicative[F].pure {
      val uriList: List[NonEmptyString] = List(
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/req/core",
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/req/oas30",
        "http://www.opengis.net/spec/ogcapi-features-1/1.0/req/geojson"
      )
      Right(Conformance(uriList).asJson)
    }
  }

  val routes
    : HttpRoutes[F] = LandingPageEndpoints.landingPageEndpoint.toRoutes(_ => landingPage()) <+>
    LandingPageEndpoints.conformanceEndpoint.toRoutes(_ => conformancePage())
}
