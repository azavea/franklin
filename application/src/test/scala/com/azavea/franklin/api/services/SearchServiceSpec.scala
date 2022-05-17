package com.azavea.franklin.api.services

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.{TestClient, TestServices}
import com.azavea.franklin.database.{SearchParameters, TestDatabaseSpec}
import com.azavea.franklin.datamodel.{PaginationToken, StacSearchCollection}
import com.azavea.stac4s.testing.JvmInstances._
import com.azavea.stac4s.testing._
import com.azavea.stac4s.{StacCollection, StacItem, StacLinkType}
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.syntax._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Method, Request, Uri}
import org.specs2.{ScalaCheck, Specification}

class SearchServiceSpec
    extends Specification
    with ScalaCheck
    with TestDatabaseSpec
    with Generators {
  def is           = s2"""
  This specification verifies that the Search Service sensibly finds and excludes items

  The search service should:
    - search with POST search filters                       $postSearchParametersExpectation
    - search with GET search filters                        $getSearchParametersExpectation
    - find an item with filters designed to find it         $findItemWhenExpected
    - find two items with filters designed to find it       $find2ItemsWhenExpected
    - not find items when excluded by time                  $dontFindTimeFilters
    - not find items when excluded by bbox                  $dontFindBboxFilters
    - not find items when excluded by intersection          $dontFindGeomFilters
    - not find items when excluded by collection            $dontFindCollectionFilters
    - not find items when excluded by item                  $dontFindItemFilters
"""
  val testServices = new TestServices[IO](transactor)

  val testClient = (testServices.collectionsService, testServices.collectionItemsService) mapN {
    new TestClient[IO](_, _)
  }

  private def getExclusionTest(
      name: String
  )(getFilters: StacCollection => StacItem => SearchParameters) =
    prop { (stacItem: StacItem, stacCollection: StacCollection) =>
      val params = getFilters(stacCollection)(stacItem)
      val testResult = {
        val resourceIO = testClient map { _.getCollectionItemResource(stacItem, stacCollection) }
        val requestIO = resourceIO flatMap { resource =>
          resource.use {
            case _ =>
              // doing this as a POST is important, since otherwise the `intersection` and
              // `query` params would be ignored (not that we're testing `query` here)
              val request =
                Request[IO](
                  method = Method.POST,
                  uri = Uri.unsafeFromString(s"/search")
                ).withEntity(params)
              (for {
                resp    <- testServices.searchService.routes.run(request)
                decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
              } yield decoded).value
          }
        }

        val result = requestIO.unsafeRunSync.get
        result.features.map(_.id).contains(stacItem.id)
      }

      testResult aka s"the item was included in the results for $name" must beFalse
    }

  def postSearchParametersExpectation = prop { (searchParameters: SearchParameters) =>
    val request = Request[IO](method = Method.POST, uri = Uri.fromString("/search").right.get)
      .withEntity(searchParameters)
    val result = for {
      resp    <- testServices.searchService.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.context.returned ==== searchResult.features.length
  }

  def getSearchParametersExpectation = prop { (searchParameters: SearchParameters) =>
    val queryParams = searchParameters.asQueryParameters
    val request =
      Request[IO](method = Method.GET, uri = Uri.fromString(s"/search?$queryParams").right.get)
    val result = for {
      resp    <- testServices.searchService.routes.run(request)
      decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
    } yield decoded
    val searchResult = result.value.unsafeRunSync.get
    searchResult.context.returned ==== searchResult.features.length
  }

  def findItemWhenExpected = prop { (stacItem: StacItem, stacCollection: StacCollection) =>
    val resourceIO = testClient map { _.getCollectionItemResource(stacItem, stacCollection) }
    val requestIO = resourceIO flatMap { resource =>
      resource.use {
        case (collection, (item, _)) =>
          val inclusiveParams =
            FiltersFor.inclusiveFilters(collection, item)
          val request =
            Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/search"))
              .withEntity(inclusiveParams)
          (for {
            resp    <- testServices.searchService.routes.run(request)
            decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
          } yield decoded).value
      }
    }

    val result = requestIO.unsafeRunSync.get
    result.features.head.id should beEqualTo(stacItem.id)
  }

  def find2ItemsWhenExpected = prop {
    (stacItem1: StacItem, stacItem2: StacItem, stacCollection: StacCollection) =>
      val resourceIO = testClient map {
        _.getCollectionItemsResource(stacItem1 :: stacItem2 :: Nil, stacCollection)
      }
      val requestIO = resourceIO flatMap { resource =>
        def getSearchCollection(searchParameters: SearchParameters): IO[Option[StacSearchCollection]] = {
          val request =
            Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"/search"))
              .withEntity(searchParameters.asJson)
          (for {
            resp    <- testServices.searchService.routes.run(request)
            decoded <- OptionT.liftF { resp.as[StacSearchCollection] }
          } yield decoded).value
        }

        resource.use {
          case (collection, _) =>
            val inclusiveParams =
              FiltersFor.inclusiveFilters(collection).copy(limit = NonNegInt.from(1).toOption)
            val result1 = getSearchCollection(inclusiveParams)

            val result2 = result1
              .flatMap {
                _.flatTraverse { r =>
                  /** This line intentionally decodes next token [[String]] into [[PaginationToken]] */
                  val next = r.links.collectFirst {
                    case l if l.rel == StacLinkType.Next =>
                      l.href.split("next=").last.asJson.as[PaginationToken].toOption
                  }.flatten
                  getSearchCollection(inclusiveParams.copy(next = next))
                }
              }

            (result1, result2).tupled
        }
      }

      val (Some(result1), Some(result2)) = requestIO.unsafeRunSync()

      result1.features.head.id should beEqualTo(stacItem1.id)
      result2.features.head.id should beEqualTo(stacItem2.id)
  }

  def dontFindTimeFilters =
    getExclusionTest("temporal extent")(_ => item => FiltersFor.timeFilterExcluding(item))

  def dontFindBboxFilters =
    getExclusionTest("bbox")(_ => item => FiltersFor.bboxFilterExcluding(item))

  def dontFindGeomFilters =
    getExclusionTest("geom intersection")(_ => item => FiltersFor.geomFilterExcluding(item))

  def dontFindCollectionFilters =
    getExclusionTest("collection ids")(collection =>
      _ => FiltersFor.collectionFilterExcluding(collection)
    )

  def dontFindItemFilters =
    getExclusionTest("item ids")(_ => item => FiltersFor.itemFilterExcluding(item))
}
