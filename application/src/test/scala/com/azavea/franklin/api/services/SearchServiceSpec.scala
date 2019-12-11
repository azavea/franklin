package com.azavea.franklin.api.services

import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.database.{SearchFilters, TestDatabaseSpec}
import com.azavea.franklin.datamodel.{APIStacRoot, StacSearchCollection}
import eu.timepit.refined.types.numeric.PosInt
import org.http4s.{Method, Request, Uri}
import cats.data.OptionT
import cats.effect.IO
import com.azavea.franklin.Generators
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.specs2.{ScalaCheck, Specification}

class SearchServiceSpec
    extends Specification
    with ScalaCheck
    with TestDatabaseSpec
    with Generators {
  def is = s2"""
  This specification verifies that the Search Service can run without crashing

  The model service should:
    - get correct result for root search      $rootSearchExpectation
    - search with POST search filters         $postSearchFiltersExpectation
    - search with GET search filters          $getSearchFiltersExpectation
"""

  val apiConfig: ApiConfig       = ApiConfig(PosInt(9090), PosInt(9090), "localhost", "http")
  def service: SearchService[IO] = new SearchService[IO](apiConfig, transactor)

  def rootSearchExpectation = {
    val result = for {
      searchResultRaw <- service.routes.run(
        Request[IO](method = Method.GET, uri = Uri.fromString("/stac").right.get)
      )
      decodedSearchResult <- OptionT.liftF { searchResultRaw.as[APIStacRoot] }
    } yield {
      decodedSearchResult
    }
    val searchResult = result.value.unsafeRunSync.get
    searchResult.links.length ==== 2
  }

  def postSearchFiltersExpectation = prop { (searchFilters: SearchFilters) =>
    val request = Request[IO](method = Method.POST, uri = Uri.fromString("/stac/search").right.get)
      .withEntity(searchFilters)
    val result = for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.searchMetadata.returned ==== searchResult.features.length
  }

  def getSearchFiltersExpectation = prop { (searchFilters: SearchFilters) =>
    val queryParams = searchFiltersToParams(searchFilters)
    val request =
      Request[IO](method = Method.GET, uri = Uri.fromString(s"/stac/search?$queryParams").right.get)
    val result = for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.searchMetadata.returned ==== searchResult.features.length
  }
}
