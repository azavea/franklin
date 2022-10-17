package com.azavea.franklin.api

import cats.MonadError
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all._
import com.azavea.franklin.api.services.{CollectionItemsService, CollectionsService}
import com.azavea.stac4s.{StacCollection, StacItem}
import eu.timepit.refined.auto._
import io.circe.syntax._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.implicits._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Method, Request, Uri}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Why sync? Because CirceEntityDecoder requires it :sad-trombone:
  *
  * https://github.com/http4s/http4s/blob/v0.21.4/circe/src/main/scala/org/http4s/circe/CirceEntityDecoder.scala#L7-L12
  */
class TestClient[F[_]: Sync](
    collectionsService: CollectionsService[F],
    collectionItemsService: CollectionItemsService[F]
) {

  private def createCollection(collection: StacCollection): F[StacCollection] =
    collectionsService.routes.orNotFound.run(
      Request(
        method = Method.POST,
        uri = Uri.unsafeFromString("/collections")
      ).withEntity(collection.asJson)
    ) flatMap { _.as[StacCollection] }

  private def deleteCollection(collection: StacCollection): F[Unit] = {
    val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
    collectionsService.routes.orNotFound
      .run(
        Request(
          method = Method.DELETE,
          uri = Uri.unsafeFromString(s"/collections/$encodedCollectionId")
        )
      )
      .void
  }

  private def createItemInCollection(
      collection: StacCollection,
      item: StacItem
  ): F[(StacItem, String)] = {
    val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
    for {
      resp <- collectionItemsService.routes.orNotFound.run(
        Request(
          method = Method.POST,
          uri = Uri.unsafeFromString(s"/collections/$encodedCollectionId/items")
        ).withEntity(item.copy(collection = None))
      )
      item <- resp.as[StacItem]
      etag <- resp.headers
        .find(h => h.name == CaseInsensitiveString("etag"))
        .map(h => h.value.pure[F]) getOrElse {
        MonadError[F, Throwable].raiseError(new Exception("No etag in response!"))
      }
    } yield (item, etag)
  }

  private def deleteItemInCollection(collection: StacCollection, item: StacItem): F[Unit] = {
    val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
    val encodedItemId       = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)
    collectionItemsService.routes.orNotFound
      .run(
        Request(
          method = Method.DELETE,
          uri = Uri.unsafeFromString(s"/collections/$encodedCollectionId/items/$encodedItemId")
        )
      )
      .void
  }

  def getItemResource(collection: StacCollection, item: StacItem): Resource[F, (StacItem, String)] =
    Resource.make(createItemInCollection(collection, item))({
      case (item, _) =>
        deleteItemInCollection(collection, item)
    })

  def getCollectionResource(collection: StacCollection): Resource[F, StacCollection] =
    Resource.make(createCollection(collection))(collection => deleteCollection(collection))

  def getCollectionItemResource(
      item: StacItem,
      collection: StacCollection
  ): Resource[F, (StacCollection, (StacItem, String))] =
    (getCollectionResource(collection), getItemResource(collection, item)).tupled

  def getCollectionItemsResource(
      items: List[StacItem],
      collection: StacCollection
  ): Resource[F, (StacCollection, List[(StacItem, String)])] =
    (getCollectionResource(collection), items.traverse(getItemResource(collection, _))).tupled
}
