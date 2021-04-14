package com.azavea.franklin.api.services

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.apply._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.{TestClient, TestServices}
import com.azavea.franklin.database.TestDatabaseSpec
import com.azavea.franklin.datamodel.CollectionItemsResponse
import com.azavea.stac4s.testing.JvmInstances._
import com.azavea.stac4s.testing._
import com.azavea.stac4s.{StacCollection, StacItem}
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Header, Headers}
import org.http4s.{Method, Request, Uri}
import org.specs2.execute.Result
import org.specs2.matcher.MatchResult
import org.specs2.{ScalaCheck, Specification}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CollectionItemsServiceSpec
    extends Specification
    with ScalaCheck
    with TestDatabaseSpec
    with Generators {
  def is = s2"""
  This specification verifies that the collections service can run without crashing

  The collection items service should:
    - create and delete items         $createDeleteItemExpectation
    - list items                      $listCollectionItemsExpectation
    - update an item                  $updateItemExpectation
    - patch an item                   $patchItemExpectation
    - get an item                     $getCollectionItemExpectation
"""

  val testServices = new TestServices[IO](transactor)

  val testClient = (testServices.collectionsService, testServices.collectionItemsService) mapN {
    new TestClient[IO](_, _)
  }

  def listCollectionItemsExpectation = prop {
    (stacCollection: StacCollection, stacItem: StacItem) =>
      val listIO = (testClient, testServices.collectionItemsService).tupled flatMap {
        case (client, collectionItemsService) =>
          client.getCollectionItemResource(stacItem, stacCollection) use {
            case (collection, _) =>
              val encodedCollectionId =
                URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
              val request = Request[IO](
                method = Method.GET,
                Uri.unsafeFromString(s"/collections/$encodedCollectionId/items")
              )
              (for {
                response <- collectionItemsService.routes.run(request)
                decoded  <- OptionT.liftF { response.as[CollectionItemsResponse] }
              } yield decoded).value
          }
      }

      val result = listIO.unsafeRunSync.get.features map { _.id }

      result must contain(stacItem.id)
  }

  // since creation / deletion is a part of the collection item resource, and accurate creation is checked
  // in getCollectionItemExpectation, this test just:
  // - makes sure that if other tests are failing, it's not because create/delete are broken.
  // - makes sure that the collection extent is correctly grown to include the item (because the generators
  //   have two wholly independent samples for the bboxes)

  def createDeleteItemExpectation = prop { (stacCollection: StacCollection, stacItem: StacItem) =>
    val testIO: IO[Result] = (testClient, testServices.collectionsService).tupled flatMap {
      case (client, collectionsService) =>
        client.getCollectionItemResource(stacItem, stacCollection) use {
          case (collection, item) =>
            val encodedCollectionId =
              URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
            val request = Request[IO](
              method = Method.GET,
              Uri.unsafeFromString(s"/collections/$encodedCollectionId")
            )

            (for {
              resp              <- collectionsService.routes.run(request)
              decodedCollection <- OptionT.liftF(resp.as[StacCollection])
            } yield {
              val collectionBbox = decodedCollection.extent.spatial.bbox.head
              (collectionBbox.union(item.bbox) must beTypedEqualTo(collectionBbox)): Result
            }).getOrElse({
              failure: Result
            })
        }
    }

    testIO.unsafeRunSync
  }

  def getCollectionItemExpectation = prop { (stacCollection: StacCollection, stacItem: StacItem) =>
    val fetchIO = (testClient, testServices.collectionItemsService).tupled flatMap {
      case (client, collectionItemsService) =>
        client.getCollectionItemResource(stacItem, stacCollection) use {
          case (collection, item) =>
            val encodedCollectionId =
              URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
            val encodedItemId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)
            val request = Request[IO](
              method = Method.GET,
              Uri.unsafeFromString(s"/collections/$encodedCollectionId/items/$encodedItemId")
            )

            (for {
              response <- collectionItemsService.routes.run(request)
              decoded  <- OptionT.liftF { response.as[StacItem] }
            } yield decoded).value
        }
    }

    val result = fetchIO.unsafeRunSync.get

    // can't test properties or links without removing the validation extension fields
    // stac4s#115
    (result.stacExtensions should beTypedEqualTo(stacItem.stacExtensions)) and
      (result.assets should beTypedEqualTo(stacItem.assets)) and
      (result.geometry should beTypedEqualTo(stacItem.geometry)) and
      (result.bbox should beTypedEqualTo(stacItem.bbox))

  }

  def updateItemExpectation = prop {
    (stacCollection: StacCollection, stacItem: StacItem, update: StacItem) =>
      val updateIO = (testClient, testServices.collectionItemsService).tupled flatMap {
        case (client, collectionItemsService) =>
          client.getCollectionItemResource(stacItem, stacCollection) use {
            case (collection, item) =>
              val encodedCollectionId =
                URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
              val encodedItemId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)
              val etag          = item.##
              val toUpdate = update.copy(
                links = item.links,
                id = item.id
              )
              val request = Request[IO](
                method = Method.PUT,
                Uri.unsafeFromString(s"/collections/$encodedCollectionId/items/$encodedItemId"),
                headers = Headers.of(Header("If-Match", s"$etag"))
              ).withEntity(toUpdate)
              (for {
                response <- collectionItemsService.routes.run(request)
                decoded  <- OptionT.liftF { response.as[StacItem] }
              } yield decoded).value

          }
      }

      val updated = updateIO.unsafeRunSync.get

      (updated.stacExtensions should beTypedEqualTo(update.stacExtensions)) and
        (updated.assets should beTypedEqualTo(update.assets)) and
        (updated.geometry should beTypedEqualTo(update.geometry)) and
        (updated.bbox should beTypedEqualTo(update.bbox))
  }

  def patchItemExpectation = prop { (stacCollection: StacCollection, stacItem: StacItem) =>
    val updateIO = (testClient, testServices.collectionItemsService).tupled flatMap {
      case (client, collectionItemsService) =>
        client.getCollectionItemResource(stacItem, stacCollection) use {
          case (collection, item) =>
            val encodedCollectionId =
              URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
            val encodedItemId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)
            val etag          = item.##
            val patch         = Map("properties" -> Map("veryUnlikelyProperty" -> true).asJson)

            val request = Request[IO](
              method = Method.PATCH,
              Uri.unsafeFromString(s"/collections/$encodedCollectionId/items/$encodedItemId"),
              headers = Headers.of(Header("If-Match", s"$etag"))
            ).withEntity(patch)

            (for {
              response <- collectionItemsService.routes.run(request)
              decoded  <- OptionT.liftF { response.as[StacItem] }
            } yield decoded).value
        }
    }

    val result = updateIO.unsafeRunSync
    result flatMap { res =>
      res.properties.asJson.as[Map[String, Json]].toOption
    } flatMap {
      _.get("veryUnlikelyProperty")
    } flatMap {
      _.as[Boolean].toOption
    } must beSome(true)
  }

}
