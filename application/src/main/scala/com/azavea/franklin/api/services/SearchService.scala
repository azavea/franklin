package com.azavea.franklin.api.services

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

object UpdateSearchResults {
  def addLink(link: Json): Json => Json = root.links.arr.modify({ arr: Vector[Json] => arr :+ link })

  val next = root.next.string
  val rmNext = root.at("next").set(None)
  def addNextGetLink(res: Json): Json = {
    next.getOption(res) match {
      case Some(nextPage) =>
        val nextLink = Link(
          "/search" + nextPage,
          StacLinkType.Next,
          Some(`application/json`),
          None,
          Some(Method.GET)
        ).asJson
        val withLink = addLink(nextLink)(res)
        rmNext(withLink)
      case None =>
        rmNext(res)
    }
  }
  def addNextPostLink(res: Json): Json = {
    next.getOption(res) match {
      case Some(nextPage) =>
        val nextLink = Link(
          "/search" + nextPage,
          StacLinkType.Next,
          Some(`application/json`),
          None,
          Some(Method.POST)
        ).asJson
        val withLink = addLink(nextLink)(res)
        rmNext(withLink)
      case None =>
        rmNext(res)
    }
  }

  val prev = root.prev.string
  val rmPrev = root.at("prev").set(None)
  def addPrevGetLink(res: Json): Json = {
    prev.getOption(res) match {
      case Some(prevPage) =>
        val prevLink = Link(
          "" + prevPage,
          StacLinkType.Prev,
          Some(`application/json`),
          None,
          Some(Method.GET)
        ).asJson
        val withLink = addLink(prevLink)(res)
        rmPrev(withLink)
      case None =>
        rmPrev(res)
    }
  }
  def addPrevPostLink(res: Json): Json = {
    prev.getOption(res) match {
      case Some(prevPage) =>
        val prevLink = Link(
          "" + prevPage,
          StacLinkType.Prev,
          Some(`application/json`),
          None,
          Some(Method.POST)
        ).asJson
        val withLink = addLink(prevLink)(res)
        rmPrev(withLink)
      case None =>
        rmPrev(res)
    }
  }

  val addLinkArray = root.at("links").set(Some(Vector[Json]().asJson))
  val addAllGetLinks = (addLinkArray andThen addNextGetLink andThen addPrevGetLink)
  val addAllPostLinks = (addLinkArray andThen addNextPostLink andThen addPrevPostLink)

  def forGet(res: Json, params: SearchParameters): Json = {
    addAllGetLinks(res)
  }
  def forPost(res: Json, params: SearchParameters): Json = {
    addAllLinks(res)
  }
}

class SearchService[F[_]: Concurrent](
    apiConfig: ApiConfig,
    xa: Transactor[F],
    rootLink: StacLink
)(
    implicit contextShift: ContextShift[F],
    timerF: Timer[F],
    serverOptions: Http4sServerOptions[F]
) extends Http4sDsl[F] {

  val searchEndpoints = new SearchEndpoints[F](apiConfig)
  val defaultLimit    = apiConfig.defaultLimit

/* Analogous links to add

"links":[
  {"rel":"next","type":"application/json","method":"GET","href":"https://planetarycomputer.microsoft.com/api/stac/v1/search?limit=1000&token=next:INM-CM4-8.ssp585.2085"},
  {"rel":"root","type":"application/json","href":"https://planetarycomputer.microsoft.com/api/stac/v1/"},
  {"rel":"self","type":"application/json","href":"https://planetarycomputer.microsoft.com/api/stac/v1/search?limit=1000"}
]
*/

  def search(path: String, params: SearchParameters): F[Either[Unit, Json]] = {
    println(s"THE PATH IN QUeSTION $path")
    val limit = params.limit getOrElse defaultLimit
    val updatedParams = params.copy(limit=Some(limit), collections=List("naip"))
    for {
      searchResults <- PGStacQueries.search(updatedParams).transact(xa)
    } yield {
      searchResults match {
        case Some(res) => Either.right(UpdateSearchResults(res))
        case None => Either.left(())
      }
    }
  }
  // {
  //   for {
  //     itemsFib <- Concurrent[F].start {
  //       (StacItemDao.query
  //         .filter(searchParameters)
  //         .list(limit.value) flatMap { items =>
  //         StacItemDao.getSearchLinks(items, limit, searchParameters, apiConfig.apiHost, searchMethod) map {
  //           (items, _)
  //         }
  //       }).transact(xa)
  //     }
  //     countFib <- Concurrent[F].start {
  //       StacItemDao.getSearchContext(searchParameters).transact(xa)
  //     }
  //     ((items, links), count) <- (itemsFib, countFib).tupled.join
  //   } yield {
  //     val withApiHost = items map { _.updateLinksWithHost(apiConfig) }
  //     val searchResult =
  //       StacSearchCollection(Context(limit, items.length, count), withApiHost, links)
  //     val updatedFeatures = searchResult.features
  //       .map { item =>
  //         ((item.collection, enableTiles) match {
  //           case (Some(collectionId), true) =>
  //             item.addTilesLink(apiConfig.apiHost, collectionId, item.id)
  //           case _ => item
  //         }).addRootLink(rootLink)
  //       }

  //     Either.right(searchResult.copy(features = updatedFeatures).asJson)
  //   }
  // }

  val routes: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(searchEndpoints.searchGet)({
      case (path, searchParameters) => {
        search(path, searchParameters)
      }
    }) <+> Http4sServerInterpreter.toRoutes(searchEndpoints.searchPost)({
      case (path, searchParameters) => {
        search(path, searchParameters)
      }
    })
}
