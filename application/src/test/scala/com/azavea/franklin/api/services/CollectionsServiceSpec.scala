package com.azavea.franklin.api.services

import cats.effect.IO
import cats.implicits._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.{TestClient, TestServices}
import com.azavea.franklin.database.TestDatabaseSpec
import com.azavea.franklin.datamodel.CollectionsResponse
import com.azavea.stac4s.testing._
import com.azavea.stac4s.StacCollection
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.{Method, Request, Uri}
import org.specs2.{ScalaCheck, Specification}
import cats.data.OptionT

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CollectionsServiceSpec
    extends Specification
    with ScalaCheck
    with TestDatabaseSpec
    with Generators {
  def is = s2"""
  This specification verifies that the collections service can run without crashing

  The collections service should:
    - create and delete collections $createDeleteCollectionExpectation
    - list collections              $listCollectionsExpectation
    - get collections by id         $getCollectionsExpectation
"""

  val testServices: TestServices[IO] = new TestServices[IO](transactor)

  val testClient: TestClient[IO] =
    new TestClient[IO](testServices.collectionsService, testServices.collectionItemsService)

  def listCollectionsExpectation = prop {
    (stacCollectionA: StacCollection, stacCollectionB: StacCollection) =>
      {
        val listIO = (
          testClient.getCollectionResource(stacCollectionA),
          testClient.getCollectionResource(stacCollectionB)
        ).tupled use { _ =>
          val request = Request[IO](method = Method.GET, Uri.unsafeFromString(s"/collections"))
          (for {
            resp    <- testServices.collectionsService.routes.run(request)
            decoded <- OptionT.liftF { resp.as[CollectionsResponse] }
          } yield decoded).value
        }

        val result = listIO.unsafeRunSync.get.collections map { _.id }

        (result must contain(stacCollectionA.id)) and (result must contain(stacCollectionB.id))
      }
  }

  def getCollectionsExpectation = prop { (stacCollection: StacCollection) =>
    val fetchIO =
      testClient.getCollectionResource(stacCollection) use { collection =>
        val encodedId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
        val request =
          Request[IO](method = Method.GET, Uri.unsafeFromString(s"/collections/$encodedId"))
        (for {
          resp    <- testServices.collectionsService.routes.run(request)
          decoded <- OptionT.liftF { resp.as[StacCollection] }
        } yield (decoded, collection)).value
      }

    val (fetched, inserted) = fetchIO.unsafeRunSync.get

    fetched must beTypedEqualTo(inserted)
  }

  // since creation / deletion is a part of the collection resource, and accurate creation is checked
  // in getCollectionsExpectation, this test just makes sure that if other tests are failing, it's
  // not because create/delete are broken
  def createDeleteCollectionExpectation = prop { (stacCollection: StacCollection) =>
    (testClient
      .getCollectionResource(stacCollection) use { _ => IO.unit }).unsafeRunSync must beTypedEqualTo(
      ()
    )
  }

}
