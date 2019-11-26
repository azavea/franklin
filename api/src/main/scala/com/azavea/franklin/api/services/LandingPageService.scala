package com.azavea.franklin.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.api.endpoints.LandingPageEndpoints
import com.azavea.franklin.datamodel.{LandingPage, Link, Conformance => FranklinConformance}
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._
import geotrellis.server.stac._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString

class LandingPageService[F[_]: Sync](apiConfig: ApiConfig)(implicit contextShift: ContextShift[F])
    extends Http4sDsl[F] {

  val links = List(
    Link(
      apiConfig.apiHost,
      Self,
      Some(`application/json`),
      Some("Franklin Powered Catalog")
    ),
    Link(
      apiConfig.apiHost + "/spec.yaml",
      ServiceDesc,
      Some(VendorMediaType("application/vnd.oai.openapi+json;version=3.0")),
      Some("Open API 3 Documentation")
    ),
    Link(
      apiConfig.apiHost + "/conformance",
      Conformance,
      Some(`application/json`),
      None
    ),
    Link(
      apiConfig.apiHost + "/collections",
      Data,
      Some(`application/json`),
      Some("Collections Listing")
    ),
    Link(
      apiConfig.apiHost + "/stac/search",
      Data,
      Some(`application/geo+json`),
      Some("Franklin Powered STAC Search")
    ),
    Link(
      apiConfig.apiHost + "/stac/",
      StacRoot,
      Some(`application/json`),
      Some("Root Catalog")
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
      Right(FranklinConformance(uriList).asJson)
    }
  }

  val routes
    : HttpRoutes[F] = LandingPageEndpoints.landingPageEndpoint.toRoutes(_ => landingPage()) <+>
    LandingPageEndpoints.conformanceEndpoint.toRoutes(_ => conformancePage())
}
