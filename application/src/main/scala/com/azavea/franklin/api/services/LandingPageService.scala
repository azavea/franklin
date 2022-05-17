package com.azavea.franklin.api.services

import com.azavea.franklin
import com.azavea.franklin.api._
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database._
import com.azavea.franklin.datamodel.{LandingPage, Link, Conformance => FranklinConformance}

import cats._
import cats.effect._
import cats.syntax.all._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

class LandingPageService[F[_]: Concurrent](apiConfig: ApiConfig)(
    implicit contextShift: ContextShift[F],
    timer: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

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
      StacLinkType.VendorLinkType("search"),
      Some(`application/geo+json`),
      Some("STAC Search API")
    )
  )

  private val conformances: List[NonEmptyString] = List[NonEmptyString](
    "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core",
    "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/oas30",
    "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson",
    "https://api.stacspec.org/v1.0.0-beta.5/core",
    "https://api.stacspec.org/v1.0.0-beta.5/collections",
    "https://api.stacspec.org/v1.0.0-beta.5/ogcapi-features",
    "https://api.stacspec.org/v1.0.0-beta.5/item-search",
    "https://api.stacspec.org/v1.0.0-beta.5/item-search#context",
    "https://api.stacspec.org/v1.0.0-beta.5/item-search#query"
  ) `combine` (if (apiConfig.enableTransactions)
                 List[NonEmptyString](
                   "https://api.stacspec.org/v1.0.0-beta.5/ogcapi-features/extensions/transaction"
                 )
               else List.empty[NonEmptyString])

  def conformancePage: F[Either[Unit, FranklinConformance]] = {
    val conformance = FranklinConformance(conformances)

    Applicative[F].pure(Either.right[Unit, FranklinConformance](conformance))
  }

  def landingPage: F[Either[Unit, LandingPage]] = {
    val title: NonEmptyString = "Welcome to Franklin"
    val description: NonEmptyString =
      "An OGC API - Features, Tiles, and STAC Server"
    val landingPage = LandingPage(
      "1.0.0",
      Nil,
      Some(title),
      "Franklin STAC API",
      description,
      links,
      conformances
    )

    Applicative[F].pure(Either.right[Unit, LandingPage](landingPage))

  }

  val endpoints = new LandingPageEndpoints[F](apiConfig.path)

  val routesList = List(
    Http4sServerInterpreter.toRoutes(endpoints.conformanceEndpoint)(_ => conformancePage),
    Http4sServerInterpreter.toRoutes(endpoints.landingPageEndpoint)(_ => landingPage)
  )

  val routes: HttpRoutes[F] = routesList.foldK
}
