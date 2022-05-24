package com.azavea.franklin.api.services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.api.endpoints.SearchEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.datamodel._
import com.azavea.franklin.database.PGStacQueries

import cats.effect._
import cats.syntax.all._
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


case class UpdateSearchResults(params: SearchParameters, host: String) {

  sealed trait PageLinkType
  case object NextLink extends PageLinkType
  case object PrevLink extends PageLinkType

  sealed trait SearchMethod
  case object SearchGET extends SearchMethod
  case object SearchPOST extends SearchMethod

  // Various optics to update json
  val addLinkArray = root.at("links").set(Some(Vector[Json]().asJson))
  def addLink(link: Json): Json => Json = root.links.arr.modify({ arr: Vector[Json] => arr :+ link })
  val next = root.next.string
  val prev = root.prev.string
  val rmNext = root.at("next").set(None)
  val rmPrev = root.at("prev").set(None)

  def constructPageLink(page: String, linkType: PageLinkType, searchMethod: SearchMethod): Json = {
    val updatedParams = linkType match {
      case NextLink => params.copy(token=s"next:$page".some)
      case PrevLink => params.copy(token=s"prev:$page".some)
    }
    val stacLinkType = linkType match {
      case NextLink => StacLinkType.Next
      case PrevLink => StacLinkType.Prev
    }
    val body: Option[Json] = searchMethod match {
      case SearchGET => None
      case SearchPOST => updatedParams.asJson.some
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
      body=body
    ).asJson
  }

  def constructSelfLink(searchMethod: SearchMethod): Json = {
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
        ).asJson
      case SearchPOST =>
        Link(
          host + "/search",
          StacLinkType.Self,
          Some(`application/json`),
          None,
          Some(Method.POST),
          params.asJson.some
        ).asJson
    }
  }
  def constructRootLink: Json = {
    Link(
      host,
      StacLinkType.StacRoot,
      Some(`application/json`),
      None,
      Some(Method.GET)
    ).asJson
  }

  def addLinks(res: Json, searchMethod: SearchMethod): Json = {
    val withLinkArray = addLinkArray(res)
    val withNextLink: Json = next.getOption(withLinkArray).map({ nextPage =>
      val link = constructPageLink(nextPage, NextLink, searchMethod)
      addLink(link)(withLinkArray)
    }).getOrElse(withLinkArray)
    val withPrevLink: Json = prev.getOption(withNextLink).map({ prevPage =>
      val link = constructPageLink(prevPage, PrevLink, searchMethod)
      addLink(link)(withNextLink)
    }).getOrElse(withNextLink)
    val withSelfLink = addLink(constructSelfLink(searchMethod))(withPrevLink)
    val withRootLink = addLink(constructRootLink)(withSelfLink)
    (rmNext andThen rmPrev)(withRootLink)
  }


  def GET(res: Json): Json = {
    addLinks(res, SearchGET).deepDropNullValues
  }
  def POST(res: Json): Json = {
    addLinks(res, SearchPOST).deepDropNullValues
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

  val searchEndpoints = new SearchEndpoints[F](apiConfig)
  val defaultLimit    = apiConfig.defaultLimit

  def search(params: SearchParameters): F[Either[Unit, Json]] = {
    val limit = params.limit getOrElse defaultLimit
    val updatedParams = params.copy(limit=Some(limit))
    for {
      searchResults <- PGStacQueries.search(updatedParams).transact(xa)
    } yield {
      searchResults match {
        case Some(res) => Either.right(res)
        case None => Either.left(())
      }
    }
  }

  val searchRouteGet =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)({ case searchParameters =>
      search(searchParameters)
        .map(_.map(UpdateSearchResults(searchParameters, apiConfig.apiHost).GET))
    })
  val searchRoutePost =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)({ case searchParameters =>
      search(searchParameters)
        .map(_.map(UpdateSearchResults(searchParameters, apiConfig.apiHost).POST))
    })
  val routes: HttpRoutes[F] =
    searchRouteGet <+> searchRoutePost
}
