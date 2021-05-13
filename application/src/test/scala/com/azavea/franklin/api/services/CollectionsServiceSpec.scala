package com.azavea.franklin.api.services

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.{TestClient, TestServices}
import com.azavea.franklin.database.TestDatabaseSpec
import com.azavea.franklin.datamodel.CollectionsResponse
import com.azavea.stac4s.testing.JvmInstances._
import com.azavea.stac4s.testing._
import com.azavea.stac4s.{StacCollection, StacLinkType}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.{Method, Request, Uri}
import org.specs2.{ScalaCheck, Specification}

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

  val testClient: IO[TestClient[IO]] =
    (testServices.collectionsService, testServices.collectionItemsService) mapN {
      new TestClient[IO](_, _)
    }

  def listCollectionsExpectation = prop {
    (stacCollectionA: StacCollection, stacCollectionB: StacCollection) =>
      {
        val listIO = (testClient, testServices.collectionsService).tupled flatMap {
          case (client, collectionsService) =>
            (
              client.getCollectionResource(stacCollectionA),
              client.getCollectionResource(stacCollectionB)
            ).tupled use { _ =>
              val request = Request[IO](method = Method.GET, Uri.unsafeFromString(s"/collections"))
              (for {
                resp    <- collectionsService.routes.run(request)
                decoded <- OptionT.liftF { resp.as[CollectionsResponse] }
              } yield decoded).value
            }
        }

        val result     = listIO.unsafeRunSync.get
        val ids        = result.collections map { _.id }
        val childHrefs = result.links.filter(_.rel == StacLinkType.Child).map(_.href).toSet
        val selfHrefs = (result.collections flatMap {
          _.links.filter(_.rel == StacLinkType.Self).map(_.href)
        }).toSet

        (ids must contain(stacCollectionA.id)) and (ids must contain(stacCollectionB.id)) and (selfHrefs & childHrefs must beEqualTo(
          selfHrefs
        ))
      }
  }

  def getCollectionsExpectation = prop { (stacCollection: StacCollection) =>
    val fetchIO =
      (testClient, testServices.collectionsService).tupled flatMap {
        case (client, collectionsService) =>
          client.getCollectionResource(stacCollection) use { collection =>
            val encodedId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
            val request =
              Request[IO](method = Method.GET, Uri.unsafeFromString(s"/collections/$encodedId"))
            (for {
              resp    <- collectionsService.routes.run(request)
              decoded <- OptionT.liftF { resp.as[StacCollection] }
            } yield (decoded, collection)).value
          }
      }

    val (fetched, inserted) = fetchIO.unsafeRunSync.get

    fetched must beTypedEqualTo(inserted)
  }

  // since creation / deletion is a part of the collection resource, and accurate creation is checked
  // in getCollectionsExpectation, this test just makes sure that if other tests are failing, it's
  // not because create/delete are broken
  def createDeleteCollectionExpectation = prop { (stacCollection: StacCollection) =>
    (testClient flatMap { _.getCollectionResource(stacCollection) use { _ => IO.unit } }).unsafeRunSync must beTypedEqualTo(
      ()
    )
  }

}
