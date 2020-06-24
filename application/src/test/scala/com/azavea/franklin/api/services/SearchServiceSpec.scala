package com.azavea.franklin.api.services

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.TestClient
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.database.{SearchFilters, TestDatabaseSpec}
import com.azavea.franklin.datamodel.StacSearchCollection
import com.azavea.stac4s.testing._
import com.azavea.stac4s.{StacCollection, StacItem}
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
  This specification verifies that the Search Service sensibly finds and excludes items

  The search service should:
    - search with POST search filters               $postSearchFiltersExpectation
    - search with GET search filters                $getSearchFiltersExpectation
    - find an item with filters designed to find it $findItemWhenExpected
    - not find items when excluded by time          $dontFindTimeFilters
    - not find items when excluded by bbox          $dontFindBboxFilters
    - not find items when excluded by intersection  $dontFindGeomFilters
    - not find items when excluded by collection    $dontFindCollectionFilters
    - not find items when excluded by item          $dontFindItemFilters
"""

  val apiConfig: ApiConfig =
    ApiConfig(PosInt(9090), PosInt(9090), "localhost", "http", NonNegInt(30), true, false)

  def service: SearchService[IO] =
    new SearchService[IO](apiConfig.apiHost, NonNegInt(30), apiConfig.enableTiles, transactor)

  val collectionsService: CollectionsService[IO] = new CollectionsService[IO](
    transactor,
    apiConfig
  )

  val collectionItemsService: CollectionItemsService[IO] = new CollectionItemsService[IO](
    transactor,
    apiConfig
  )

  val testClient = new TestClient[IO](collectionsService, collectionItemsService)

  private def getExclusionTest(
      name: String
  )(getFilters: StacCollection => StacItem => Option[SearchFilters]) =
    prop { (stacItem: StacItem, stacCollection: StacCollection) =>
      val exclusiveParams = getFilters(stacCollection)(stacItem)
      val testResult = exclusiveParams.map({ params =>
        val resource = testClient.getResource(stacItem, stacCollection)
        val requestIO = resource.use {
          case _ =>
            // doing this as a POST is important, since otherwise the `intersection` and
            // `query` params would be ignored (not that we're testing `query` here)
            val request =
              Request[IO](
                method = Method.POST,
                uri = Uri.unsafeFromString(s"/search")
              ).withEntity(params)
            (for {
              resp    <- service.routes.run(request)
              decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
            } yield decoded).value
        }

        val result = requestIO.unsafeRunSync.get
        result.features.map(_.id).contains(stacItem.id)
      })

      (testResult must beSome(false)) or (testResult must beNone and skipped(
        s"$name did not produce filters"
      ))
    }

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
          FiltersFor.inclusiveFilters(collection, item)
        val request =
          Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/search"))
            .withEntity(inclusiveParams)
        (for {
          resp    <- service.routes.run(request)
          decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
        } yield decoded).value
    }

    val result = requestIO.unsafeRunSync.get
    result.features.head.id should beEqualTo(stacItem.id)
  }

  def dontFindTimeFilters =
    getExclusionTest("temporal extent")(_ => item => FiltersFor.timeFilterExcluding(item))

  def dontFindBboxFilters =
    getExclusionTest("bbox")(_ => item => FiltersFor.bboxFilterExcluding(item).some)

  def dontFindGeomFilters =
    getExclusionTest("geom intersection")(_ => item => FiltersFor.geomFilterExcluding(item).some)

  def dontFindCollectionFilters =
    getExclusionTest("collection ids")(collection =>
      _ => FiltersFor.collectionFilterExcluding(collection).some
    )

  def dontFindItemFilters =
    getExclusionTest("item ids")(_ => item => FiltersFor.itemFilterExcluding(item).some)
}
