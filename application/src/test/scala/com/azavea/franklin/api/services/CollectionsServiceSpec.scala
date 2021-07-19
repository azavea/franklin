package com.azavea.franklin.api.services

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.{TestClient, TestServices}
import com.azavea.franklin.database.TestDatabaseSpec
import com.azavea.franklin.datamodel.CollectionsResponse
import com.azavea.franklin.datamodel.ItemAsset
import com.azavea.franklin.datamodel.MapCenter
import com.azavea.franklin.datamodel.MosaicDefinition
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.testing.JvmInstances._
import com.azavea.stac4s.testing._
import com.azavea.stac4s.{StacCollection, StacLinkType}
import io.circe.syntax._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.{Method, Request, Uri}
import org.scalacheck.Prop
import org.specs2.execute.Result
import org.specs2.{ScalaCheck, Specification}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

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
    - create a mosaic definition    $createMosaicDefinitionExpectation
    - get a mosaic by id            $getMosaicExpectation
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

  @SuppressWarnings(Array("TraversableHead"))
  def createMosaicDefinitionExpectation =
    (prop { (stacCollection: StacCollection, stacItem: StacItem) =>
      val expectationIO = (testClient, testServices.collectionsService).tupled flatMap {
        case (client, collectionsService) =>
          client.getCollectionItemsResource(List(stacItem), stacCollection) use {
            case (collection, items) =>
              val encodedCollectionId =
                URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
              val item = items.head
              val mosaicDefinition = if (item.assets.isEmpty) {
                val name = "bogus asset name"
                MosaicDefinition(
                  UUID.randomUUID,
                  Option("Testing mosaic definition"),
                  MapCenter.fromGeometry(item.geometry, 8),
                  NonEmptyList.of(ItemAsset(item.id, name)),
                  2,
                  30,
                  item.bbox
                )
              } else {
                val name = item.assets.keys.head
                MosaicDefinition(
                  UUID.randomUUID,
                  Option("Testing mosaic definition"),
                  MapCenter.fromGeometry(item.geometry, 8),
                  NonEmptyList.of(ItemAsset(item.id, name)),
                  2,
                  30,
                  item.bbox
                )
              }

              val request =
                Request[IO](
                  method = Method.POST,
                  Uri.unsafeFromString(s"/collections/$encodedCollectionId/mosaic")
                ).withEntity(
                  mosaicDefinition
                )

              collectionsService.routes.run(request).value flatMap {
                case Some(resp) =>
                  if (stacItem.assets.isEmpty) {
                    IO.pure(resp.status.code must beTypedEqualTo(404): Result)
                  } else {
                    resp.as[MosaicDefinition] map { result =>
                      result must beEqualTo(mosaicDefinition): Result
                    }
                  }
                case None => IO.pure(failure: Result)
              }
          }

      }
      expectationIO.unsafeRunSync

    }).pendingUntilFixed(
      "Creating mosaics relies on actual assets that we can read histograms from"
    )

  @SuppressWarnings(Array("TraversableHead"))
  def getMosaicExpectation =
    (prop { (stacCollection: StacCollection, stacItem: StacItem) =>
      (!stacItem.assets.isEmpty) ==> {
        val expectationIO = (testClient, testServices.collectionsService).tupled flatMap {
          case (client, collectionsService) =>
            client.getCollectionItemsResource(List(stacItem), stacCollection) use {
              case (collection, items) =>
                val encodedCollectionId =
                  URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
                val item = items.head
                val name = item.assets.keys.head
                val mosaicDefinition =
                  MosaicDefinition(
                    UUID.randomUUID,
                    Option("Testing mosaic definition"),
                    MapCenter.fromGeometry(item.geometry, 8),
                    NonEmptyList.of(ItemAsset(item.id, name)),
                    2,
                    30,
                    item.bbox
                  )

                val createRequest =
                  Request[IO](
                    method = Method.POST,
                    Uri.unsafeFromString(s"/collections/$encodedCollectionId/mosaic")
                  ).withEntity(
                    mosaicDefinition
                  )

                val getRequest =
                  Request[IO](
                    method = Method.GET,
                    Uri.unsafeFromString(
                      s"/collections/$encodedCollectionId/mosaic/${mosaicDefinition.id}"
                    )
                  )

                val deleteRequest =
                  Request[IO](
                    method = Method.DELETE,
                    Uri.unsafeFromString(
                      s"/collections/$encodedCollectionId/mosaic/${mosaicDefinition.id}"
                    )
                  )

                (for {
                  _          <- collectionsService.routes.run(createRequest)
                  resp       <- collectionsService.routes.run(getRequest)
                  decoded    <- OptionT.liftF { resp.as[MosaicDefinition] }
                  deleteResp <- collectionsService.routes.run(deleteRequest)
                } yield (decoded, deleteResp)).value map {
                  case Some((respData, deleteResp)) =>
                    respData === mosaicDefinition && deleteResp.status.code === 204: Prop
                  case None => false: Prop
                }
            }

        }
        expectationIO.unsafeRunSync
      }
    }).pendingUntilFixed(
      "Creating mosaics relies on actual assets that we can read histograms from"
    )
}
