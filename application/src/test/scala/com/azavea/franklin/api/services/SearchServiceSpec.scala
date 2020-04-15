package com.azavea.franklin.api.services

import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.database.{SearchFilters, TestDatabaseSpec}
import com.azavea.franklin.datamodel.StacSearchCollection
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
    - search with POST search filters         $postSearchFiltersExpectation
    - search with GET search filters          $getSearchFiltersExpectation
"""

  val apiConfig: ApiConfig       = ApiConfig(PosInt(9090), PosInt(9090), "localhost", "http", false, false)
  def service: SearchService[IO] = new SearchService[IO](apiConfig.apiHost, apiConfig.enableTiles, transactor)

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
    val queryParams = searchFiltersToParams(searchFilters)
    val request =
      Request[IO](method = Method.GET, uri = Uri.fromString(s"/search?$queryParams").right.get)
    val result = for {
      resp    <- service.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.context.returned ==== searchResult.features.length
  }
}
