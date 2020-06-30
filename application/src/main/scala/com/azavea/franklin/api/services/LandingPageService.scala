package com.azavea.franklin.api.services

import cats._
import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.api._
import com.azavea.franklin.datamodel.{LandingPage, Link, Conformance => FranklinConformance}
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._
import com.azavea.franklin

class LandingPageService[F[_]: Sync](apiConfig: ApiConfig)(implicit contextShift: ContextShift[F])
    extends Http4sDsl[F] {

  val links = List(
    Link(
      apiConfig.apiHost,
      StacLinkType.Self,
      Some(`application/json`),
      Some("Franklin Powered Catalog")
    ),
    Link(
      apiConfig.apiHost + "/open-api/spec.yaml",
      StacLinkType.ServiceDesc,
      Some(VendorMediaType("application/vnd.oai.openapi+json;version=3.0")),
      Some("Open API 3 Documentation")
    ),
    Link(
      apiConfig.apiHost + "/conformance",
      StacLinkType.Conformance,
      Some(`application/json`),
      Some("Conformance")
    ),
    Link(
      apiConfig.apiHost + "/collections",
      StacLinkType.Data,
      Some(`application/json`),
      Some("Collections Listing")
    ),
    Link(
      apiConfig.apiHost + "/search",
      StacLinkType.Data,
      Some(`application/geo+json`),
      Some("STAC Search API")
    )
  )

  def conformancePage(acceptHeader: AcceptHeader): F[Either[Unit, (String, fs2.Stream[F, Byte])]] = {
    val uriList: List[NonEmptyString] = List(
      "http://www.opengis.net/spec/ogcapi-features-1/1.0/req/core",
      "http://www.opengis.net/spec/ogcapi-features-1/1.0/req/oas30",
      "http://www.opengis.net/spec/ogcapi-features-1/1.0/req/geojson"
    )
    val conformance = FranklinConformance(uriList)
    val jsonOutput  = conformance.asJson
    val html        = franklin.html.conformance(conformance).body

    Applicative[F].pure(handleOut(html, jsonOutput, acceptHeader))
  }

  def landingPage(
      acceptHeader: AcceptHeader
  ): F[Either[Unit, (String, fs2.Stream[F, Byte])]] = {
    val title: NonEmptyString = "Welcome to Franklin"
    val description: NonEmptyString =
      "An OGC API - Features, Tiles, and STAC Server"
    val landingPage = LandingPage(title, description, links)
    val text   = franklin.html.index(landingPage)
    val result = handleOut(text.body, landingPage.asJson, acceptHeader)
    println(s"Accept Json: ${acceptHeader.acceptJson}")
    Applicative[F].pure(result)
  }

  val endpoints = new LandingPageEndpoints()

  val routes: HttpRoutes[F] = endpoints.conformanceEndpoint.toRoutes(acceptHeader =>
    conformancePage(acceptHeader)
  ) <+> endpoints.landingPageEndpoint.toRoutes(headers => landingPage(headers))
}
