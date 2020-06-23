package com.azavea.franklin.api.services

import cats.data.OptionT
import cats.effect.IO
import com.azavea.franklin.Generators
import com.azavea.franklin.api.TestClient
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.database.{SearchFilters, TestDatabaseSpec}
import com.azavea.franklin.datamodel.StacSearchCollection
import com.azavea.stac4s.{StacCollection, StacItem}
import com.azavea.stac4s.testing._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Method, Request, Uri}
import org.specs2.{ScalaCheck, Specification}

class SearchServiceSpec
    extends Specification
    with ScalaCheck
    with TestDatabaseSpec
    with Generators {
  def is = s2"""
  This specification verifies that the Search Service can run without crashing

  The search service should:
    - search with POST search filters         $postSearchFiltersExpectation
    - search with GET search filters          $getSearchFiltersExpectation
"""

  val apiConfig: ApiConfig =
    ApiConfig(PosInt(9090), PosInt(9090), "localhost", "http", NonNegInt(30), true, false)

  def service: SearchService[IO] =
    new SearchService[IO](apiConfig.apiHost, NonNegInt(30), apiConfig.enableTiles, transactor)

  val collectionsService: CollectionsService[IO] = new CollectionsService[IO](
    transactor,
    apiConfig.apiHost,
    apiConfig.enableTransactions,
    apiConfig.enableTiles
  )

  val collectionItemsService: CollectionItemsService[IO] = new CollectionItemsService[IO](
    transactor,
    apiConfig.apiHost,
    apiConfig.defaultLimit,
    apiConfig.enableTransactions,
    apiConfig.enableTiles
  )

  val testClient = new TestClient[IO](collectionsService, collectionItemsService)

  def postSearchFiltersExpectation = prop { (searchFilters: SearchFilters) =>
    val request = Request[IO](method = Method.POST, uri = Uri.fromString("/search").right.get)
      .withEntity(searchFilters)
    val result = for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.context.returned ==== searchResult.features.length
  }

  def getSearchFiltersExpectation = prop { (searchFilters: SearchFilters) =>
    val queryParams = searchFilters.asQueryParameters
    val request =
      Request[IO](method = Method.GET, uri = Uri.fromString(s"/search?$queryParams").right.get)
    val result = for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.context.returned ==== searchResult.features.length
  }

  def findItemWhenExpected = prop { (stacItem: StacItem, stacCollection: StacCollection) =>
    val resource = testClient.getResource(stacItem, stacCollection)
    val requestIO = resource.use {
      case (item, collection) =>
        val inclusiveParams =
          FiltersFor.inclusiveFilters(collection, item).asQueryParameters
        val request =
          Request[IO](method = Method.GET, uri = Uri.unsafeFromString(s"/search?$inclusiveParams"))
        (for {
          resp    <- service.routes.run(request)
          decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
        } yield decoded).value
    }

    val result = requestIO.unsafeRunSync.get
    result.features.head.id should beEqualTo(stacItem.id)
  }
}
