package com.azavea.franklin.api.services

import com.azavea.franklin.api.endpoints.CollectionItemEndpoints
import com.azavea.franklin.error.{CrudError, NotFound => NF, ValidationError}
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.database.Filterables._

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import com.azavea.stac4s.{`application/json`, Parent, StacItem, StacLink}
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._
import eu.timepit.refined.auto._
import org.http4s.HttpRoutes

class CollectionItemsService[F[_]: Sync](
    xa: Transactor[F],
    apiHost: NonEmptyString,
    enableTransactions: Boolean
)(
    implicit contextShift: ContextShift[F]
) extends Http4sDsl[F] {

  def collectionFilter(collectionId: String): Fragment = {
    val jsonFilter = s"""{"collection": "$collectionId"}"""
    fr"""item @> $jsonFilter :: jsonb"""
  }

  def listCollectionItems(collectionId: String): F[Either[Unit, Json]] = {
    for {
      items <- StacItemDao.query
        .filter(collectionFilter(collectionId))
        .list
        .transact(xa)
    } yield {
      val response = Json.obj(
        ("type", Json.fromString("FeatureCollection")),
        ("features", items.asJson)
      )
      Either.right(response)
    }

  }

  def getCollectionItemUnique(collectionId: String, itemId: String): F[Either[NF, Json]] = {
    for {
      itemOption <- StacItemDao.query
        .filter(fr"id = $itemId")
        .filter(collectionFilter(collectionId))
        .selectOption
        .transact(xa)
    } yield {
      Either.fromOption(
        itemOption map { _.asJson },
        NF(s"Item $itemId in collection $collectionId not found")
      )
    }
  }

  def postItem(collectionId: String, item: StacItem): F[Either[ValidationError, Json]] = {
    val fallbackCollectionLink = StacLink(
      s"$apiHost/api/collections/$collectionId",
      Parent,
      Some(`application/json`),
      Some("Parent collection"),
      Nil
    )
    item.collection match {
      case Some(collId) =>
        if (collectionId == collId) {
          val parentLink = item.links.filter(_.rel == Parent).headOption map { existingLink =>
            existingLink.copy(href = s"$apiHost/api/collections/$collectionId")
          } getOrElse {
            fallbackCollectionLink
          }
          val withParent =
            item.copy(links = parentLink +: item.links.filter(_.rel != Parent))
          StacItemDao.insertStacItem(withParent).transact(xa) map { inserted =>
            Right(inserted.asJson)
          }
        } else {
          Applicative[F].pure(
            Left(
              ValidationError(
                s"Collection ID in item $collId did not match collection ID in route $collectionId"
              )
            )
          )
        }
      case None =>
        val withParent = item.copy(links = fallbackCollectionLink +: item.links)
        StacItemDao.insertStacItem(withParent).transact(xa) map { inserted =>
          Right(inserted.asJson)
        }
    }
  }

  def putItem(
      collectionId: String,
      itemId: String,
      itemUpdate: StacItem,
      etag: String
  ): F[Either[CrudError, Json]] =
    ???

  val collectionItemEndpoints = new CollectionItemEndpoints(enableTransactions)

  val transactionRoutes: List[HttpRoutes[F]] = List(
    collectionItemEndpoints.postItem.toRoutes {
      case (collectionId, stacItem) => postItem(collectionId, stacItem)
    },
    collectionItemEndpoints.putItem.toRoutes {
      case (collectionId, itemId, stacItem, etag) => putItem(collectionId, itemId, stacItem, etag)
    }
  )

  val routesList: NonEmptyList[HttpRoutes[F]] = NonEmptyList.of(
    collectionItemEndpoints.collectionItemsList.toRoutes { collectionId =>
      listCollectionItems(collectionId)
    },
    collectionItemEndpoints.collectionItemsUnique.toRoutes {
      case (collectionId, itemId) => getCollectionItemUnique(collectionId, itemId)
    }
  ) ++ (if (enableTransactions) {
          transactionRoutes
        } else {
          List.empty
        })

  val routes: HttpRoutes[F] = routesList.foldK
}
