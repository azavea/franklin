package com.azavea.franklin.api.services

import com.azavea.franklin
import com.azavea.franklin.api._
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database._
import com.azavea.franklin.datamodel.{Catalog, Link, Conformance => FranklinConformance}
import com.azavea.franklin.datamodel.hierarchy.StacHierarchy

import cats._
import cats.effect._
import cats.syntax.all._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import io.circe.Json
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._


class LandingPageService[F[_]: Concurrent](
  apiConfig: ApiConfig
)(
  implicit contextShift: ContextShift[F],
  timer: Timer[F],
  serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  val apiHost = apiConfig.apiHost
  val stacHierarchy = apiConfig.stacHierarchy

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
  ) ++ stacHierarchy.childLinks(apiHost) ++ stacHierarchy.itemLinks(apiHost)

  private val conformances: List[String] = List[String](
    "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core",
    "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/oas30",
    "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson",
    "https://api.stacspec.org/v1.0.0-rc.1/core",
    "https://api.stacspec.org/v1.0.0-rc.1/collections",
    "https://api.stacspec.org/v1.0.0-rc.1/ogcapi-features",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#context",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#fields",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#filter:basic-cql",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#filter:cql-json",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#filter:cql-text",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#filter:filter",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#filter:item-search-filter",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#query",
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#sort",
    "https://api.stacspec.org/v1.0.0-rc.1/ogcapi-features",
    // filter
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#filter",
    "http://www.opengis.net/spec/ogcapi-features-3/1.0/conf/filter",
    "http://www.opengis.net/spec/ogcapi-features-3/1.0/conf/features-filter",
    "http://www.opengis.net/spec/cql2/1.0/conf/cql2-text",
    "http://www.opengis.net/spec/cql2/1.0/conf/cql2-json",
    "http://www.opengis.net/spec/cql2/1.0/conf/basic-cql2",
    "http://www.opengis.net/spec/cql2/1.0/conf/advanced-comparison-operators",
    "http://www.opengis.net/spec/cql2/1.0/conf/basic-spatial-operators",
    "http://www.opengis.net/spec/cql2/1.0/conf/spatial-operators",
    "http://www.opengis.net/spec/cql2/1.0/conf/temporal-operators",
    "http://www.opengis.net/spec/cql2/1.0/conf/functions",
    "http://www.opengis.net/spec/cql2/1.0/conf/arithmetic",
    "http://www.opengis.net/spec/cql2/1.0/conf/array-operators",
    "http://www.opengis.net/spec/cql2/1.0/conf/property-property",
    "http://www.opengis.net/spec/cql2/1.0/conf/accent-case-insensitive-comparison",
    // context
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#context",
    "https://api.stacspec.org/v1.0.0-rc.1/ogcapi-features#context",
    // sort
    "https://api.stacspec.org/v1.0.0-rc.1/item-search#sort",
    "https://api.stacspec.org/v1.0.0-rc.1/ogcapi-features#sort",
    // transaction
    "https://api.stacspec.org/v1.0.0-rc.1/ogcapi-features/extensions/transaction",

  ) `combine` (if (apiConfig.enableTransactions)
                 List[String](
                   "https://api.stacspec.org/v1.0.0-rc.1/ogcapi-features/extensions/transaction"
                 )
               else List.empty[String])

  def conformancePage: F[Either[Unit, FranklinConformance]] = {
    val conformance = FranklinConformance(conformances)

    Applicative[F].pure(Either.right[Unit, FranklinConformance](conformance))
  }

  def landingPage: F[Either[Unit, Json]] = {
    val title: String = "Welcome to Franklin"
    val description: String =
      "An OGC API - Features, Tiles, and STAC Server"
    val landingPage = Catalog(
      "1.0.0",
      List(),
      "Franklin STAC API",
      Some(title),
      description,
      links,
      conformances.some
    ).asJson.deepDropNullValues

    Applicative[F].pure(Either.right[Unit, Json](landingPage))

  }

  val endpoints = new LandingPageEndpoints[F](apiConfig.path)

  val routesList = List(
    Http4sServerInterpreter.toRoutes(endpoints.conformanceEndpoint)(_ => conformancePage),
    Http4sServerInterpreter.toRoutes(endpoints.landingPageEndpoint)(_ => landingPage)
  )

  val routes: HttpRoutes[F] = routesList.foldK
}
