package com.azavea.franklin.database

import cats.data.{EitherT, OptionT}
import cats.implicits._
import com.azavea.franklin.datamodel.{Context, PaginationToken, SearchMethod, StacSearchCollection}
import com.azavea.franklin.extensions.paging.PagingLinkExtension
import com.azavea.stac4s._
import com.azavea.stac4s.syntax._
import doobie.Fragment
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.refined.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.Projected
import io.circe.syntax._
import io.circe.{DecodingFailure, Json}

import java.time.Instant

object StacItemDao extends Dao[StacItem] {

  sealed abstract class StacItemDaoError(val msg: String) extends Throwable
  case object UpdateFailed                                extends StacItemDaoError("Failed to update STAC item")
  case object StaleObject                                 extends StacItemDaoError("Server-side object updated")
  case object ItemNotFound                                extends StacItemDaoError("Not found")

  case class PatchInvalidatesItem(err: DecodingFailure)
      extends StacItemDaoError("Applying patch would create an invalid patch item")

  val tableName = "collection_items"

  val selectF = fr"SELECT item FROM " ++ tableF

  def getItemCount(): ConnectionIO[Int] = {
    sql"select count(*) from collection_items".query[Int].unique
  }

  def getAssetCount(): ConnectionIO[Int] = {
    sql"""select count(*)
          from (
            select jsonb_object_keys((item->>'assets')::jsonb) from collection_items
          ) assets""".query[Int].unique
  }

  def collectionFilter(collectionId: String): Fragment = {
    val jsonFilter = s"""{"collection": "$collectionId"}"""
    fr"item @> $jsonFilter :: jsonb"
  }

  def getPaginationToken(
      itemId: String
  ): ConnectionIO[Option[PaginationToken]] =
    (OptionT {
      query
        .copy[(PosInt, Instant)](selectF = fr"select serial_id, created_at from " ++ tableF)
        .filter(fr"id = $itemId")
        .selectOption
    } map {
      case (serialId, createdAt) => PaginationToken(createdAt, serialId)
    }).value

  def getSearchResult(
      searchFilters: SearchFilters,
      limit: NonNegInt,
      apiHost: NonEmptyString,
      searchMethod: SearchMethod
  ): ConnectionIO[StacSearchCollection] = {
    for {
      items <- query.filter(searchFilters).list(limit.value)
      nextToken <- items.lastOption traverse { item =>
        getPaginationToken(item.id)
      } map { _.flatten }
      nextExists <- nextToken traverse { token =>
        query.filter(searchFilters.copy(next = Some(token), limit = Some(1))).exists
      }
      matched <- query.filter(searchFilters.copy(next = None)).count
    } yield {
      val links = (nextExists, nextToken, searchMethod, searchFilters.asQueryParameters) match {
        case (Some(true), Some(token), SearchMethod.Get, "") =>
          List(
            StacLink(
              href =
                s"$apiHost/search?limit=$limit&next=${PaginationToken.encPaginationToken(token)}",
              rel = StacLinkType.Next,
              _type = Some(`application/json`),
              title = None
            )
          )
        case (Some(true), Some(token), SearchMethod.Get, query) =>
          List(
            StacLink(
              href =
                s"$apiHost/search?limit=$limit&next=${PaginationToken.encPaginationToken(token)}&$query",
              rel = StacLinkType.Next,
              _type = Some(`application/json`),
              title = None
            )
          )
        case (Some(true), Some(token), SearchMethod.Post, _) =>
          List(
            StacLink(
              href = s"$apiHost/search?next=${PaginationToken.encPaginationToken(token)}",
              rel = StacLinkType.Next,
              _type = Some(`application/json`),
              title = None
            ).addExtensionFields(
              PagingLinkExtension(
                Map.empty,
                searchFilters.copy(next = None),
                false
              )
            )
          )
        case _ => Nil
      }
      val metadata = Context(items.length, matched)
      StacSearchCollection(metadata, items, links)
    }
  }

  def insertStacItem(item: StacItem): ConnectionIO[StacItem] = {
    val projectedGeometry = Projected(item.geometry, 4326)

    val insertFragment = fr"""
      INSERT INTO collection_items (id, geom, item)
      VALUES
      (${item.id}, $projectedGeometry, $item)
      """
    insertFragment.update
      .withUniqueGeneratedKeys[StacItem]("item")
  }

  def getCollectionItem(collectionId: String, itemId: String): ConnectionIO[Option[StacItem]] =
    StacItemDao.query
      .filter(fr"id = $itemId")
      .filter(collectionFilter(collectionId))
      .selectOption

  private def doUpdate(itemId: String, item: StacItem): ConnectionIO[StacItem] = {
    val fragment   = fr"""
      UPDATE collection_items
      SET
        item = $item
      WHERE id = $itemId
    """
    fragment.update.withUniqueGeneratedKeys[StacItem]("item")
  }

  def updateStacItem(
      collectionId: String,
      itemId: String,
      item: StacItem,
      etag: String
  ): ConnectionIO[Either[StacItemDaoError, StacItem]] =
    (for {
      itemInDB <- EitherT.fromOptionF[ConnectionIO, StacItemDaoError, StacItem](
        getCollectionItem(collectionId, itemId),
        ItemNotFound: StacItemDaoError
      )
      etagInDb = itemInDB.##
      update <- if (etagInDb.toString == etag) {
        EitherT { doUpdate(itemId, item).attempt } leftMap { _ => UpdateFailed: StacItemDaoError }
      } else {
        EitherT.leftT[ConnectionIO, StacItem] { StaleObject: StacItemDaoError }
      }
    } yield update).value

  def patchItem(
      collectionId: String,
      itemId: String,
      jsonPatch: Json,
      etag: String
  ): ConnectionIO[Option[Either[StacItemDaoError, StacItem]]] = {
    (for {
      itemInDBOpt <- getCollectionItem(collectionId, itemId)
      update <- itemInDBOpt traverse { itemInDB =>
        val etagInDb = itemInDB.##
        val patched  = itemInDB.asJson.deepMerge(jsonPatch).dropNullValues
        val decoded  = patched.as[StacItem]
        (decoded, etagInDb.toString == etag) match {
          case (Right(patchedItem), true) =>
            doUpdate(itemId, patchedItem.copy(properties = patchedItem.properties.filter({
              case (_, v) => !v.isNull
            }))).attempt map { _.leftMap(_ => UpdateFailed: StacItemDaoError) }
          case (_, false) =>
            (Either.left[StacItemDaoError, StacItem](StaleObject)).pure[ConnectionIO]
          case (Left(err), _) =>
            (Either.left[StacItemDaoError, StacItem](PatchInvalidatesItem(err))).pure[ConnectionIO]
        }
      }
    } yield update)
  }
}
