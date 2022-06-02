package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel._
import com.azavea.stac4s._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

case class UpdateSearchResults(params: SearchParameters, host: String) {

  sealed trait PageLinkType
  case object NextLink extends PageLinkType
  case object PrevLink extends PageLinkType

  sealed trait SearchMethod
  case object SearchGET  extends SearchMethod
  case object SearchPOST extends SearchMethod

  // Various optics to update json
  def addLink(link: Link): StacSearchCollection => StacSearchCollection = { searchCollection =>
    searchCollection.copy(links = searchCollection.links :+ link)
  }
  val next   = root.next.string
  val prev   = root.prev.string
  val rmNext = root.at("next").set(None)
  val rmPrev = root.at("prev").set(None)

  def constructPageLink(page: String, linkType: PageLinkType, searchMethod: SearchMethod): Link = {
    val updatedParams = linkType match {
      case NextLink => params.copy(token = s"next:$page".some)
      case PrevLink => params.copy(token = s"prev:$page".some)
    }
    val stacLinkType = linkType match {
      case NextLink => StacLinkType.Next
      case PrevLink => StacLinkType.Prev
    }
    val body: Option[Json] = searchMethod match {
      case SearchGET  => None
      case SearchPOST => updatedParams.asJson.dropNullValues.some
    }
    val href = searchMethod match {
      case SearchGET =>
        val queryParams = updatedParams.asQueryParameters
        val queryString = if (queryParams.length > 0) "?" + queryParams else ""
        host + "/search" + queryString
      case SearchPOST =>
        host + "/search"
    }

    Link(
      href,
      stacLinkType,
      Some(`application/json`),
      None,
      Some(Method.GET),
      body = body
    )
  }

  def constructSelfLink(searchMethod: SearchMethod): Link = {
    searchMethod match {
      case SearchGET =>
        val queryParams = params.asQueryParameters
        val queryString = if (queryParams.length > 0) "?" + queryParams else ""
        Link(
          host + "/search" + queryString,
          StacLinkType.Self,
          Some(`application/json`),
          None,
          Some(Method.GET)
        )
      case SearchPOST =>
        Link(
          host + "/search",
          StacLinkType.Self,
          Some(`application/json`),
          None,
          Some(Method.POST),
          params.asJson.some
        )
    }
  }

  def constructRootLink: Link = {
    Link(
      host,
      StacLinkType.StacRoot,
      Some(`application/json`),
      None,
      Some(Method.GET)
    )
  }

  def addLinks(
      searchResults: StacSearchCollection,
      searchMethod: SearchMethod
  ): StacSearchCollection = {
    val nextPageLink = searchResults.next.map { constructPageLink(_, NextLink, searchMethod) }
    val prevPageLink = searchResults.prev.map { constructPageLink(_, PrevLink, searchMethod) }
    val selfLink     = constructSelfLink(searchMethod)
    val rootLink     = constructRootLink
    searchResults.copy(links =
      searchResults.links ++ List(nextPageLink, prevPageLink, selfLink.some, rootLink.some).flatten
    )
  }

  def GET(searchResults: StacSearchCollection): StacSearchCollection = {
    addLinks(searchResults, SearchGET)
  }

  def POST(searchResults: StacSearchCollection): StacSearchCollection = {
    addLinks(searchResults, SearchPOST)
  }
}

class SearchService[F[_]: Concurrent](
    apiConfig: ApiConfig,
    xa: Transactor[F]
)(
    implicit contextShift: ContextShift[F],
    timerF: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  implicit val MySpecialPrinter = Printer(true, "")

  val searchEndpoints = new SearchEndpoints[F](apiConfig)
  val defaultLimit    = apiConfig.defaultLimit

  def search(params: SearchParameters): F[Either[Unit, StacSearchCollection]] = {
    val limit         = params.limit getOrElse defaultLimit
    val updatedParams = params.copy(limit = Some(limit))
    for {
      searchResults <- PGStacQueries.search(updatedParams).attempt.transact(xa)
    } yield {
      searchResults.leftMap(_ => ())
    }
  }

  val searchRouteGet =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)({
      case searchParameters =>
        search(searchParameters)
          .map(_.map(UpdateSearchResults(searchParameters, apiConfig.apiHost).GET))
    })

  val searchRoutePost =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)({
      case searchParameters =>
        search(searchParameters)
          .map(_.map(UpdateSearchResults(searchParameters, apiConfig.apiHost).POST))
    })

  val routes: HttpRoutes[F] =
    searchRouteGet <+> searchRoutePost
}
