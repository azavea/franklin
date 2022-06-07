package com.azavea.franklin.api.util

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.ItemEndpoints
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel._
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.syntax._
import org.http4s.Method
import sttp.tapir.server.http4s._

case class UpdateSearchLinks(params: SearchParameters, host: String) {

  sealed trait PageLinkType
  case object NextLink extends PageLinkType
  case object PrevLink extends PageLinkType

  sealed trait SearchMethod
  case object SearchGET  extends SearchMethod
  case object SearchPOST extends SearchMethod

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
