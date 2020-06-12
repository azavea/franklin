package com.azavea.franklin.database

import cats.data.{EitherT, OptionT}
import cats.implicits._
import com.azavea.franklin.datamodel.{Context, PaginationToken, StacSearchCollection}
import com.azavea.stac4s._
import doobie.Fragment
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.refined.implicits._
import eu.timepit.refined.types.numeric.PosInt
import geotrellis.vector.Projected
import io.circe.syntax._
import io.circe.{Error, Json}

import java.time.Instant
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString

object StacItemDao extends Dao[StacItem] {

  sealed abstract class StacItemDaoError(val msg: String) extends Throwable
  case object UpdateFailed                                extends StacItemDaoError("Failed to update STAC item")
  case object StaleObject                                 extends StacItemDaoError("Server-side object updated")
  case object ItemNotFound                                extends StacItemDaoError("Not found")

  case class PatchInvalidatesItem(err: Error)
      extends StacItemDaoError("Applying patch would create an invalid patch item")

  val tableName = "collection_items"

  val selectF = fr"SELECT item FROM " ++ tableF

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
      apiHost: NonEmptyString
  ): ConnectionIO[StacSearchCollection] = {
    for {
      items     <- query.filter(searchFilters).page(Page(limit, searchFilters.next))
      nextToken <- items.lastOption traverse { item => getPaginationToken(item.id) }
      matched   <- query.filter(searchFilters).count
    } yield {
      val links = (nextToken.flatten, searchFilters.asQueryParameters) match {
        case (Some(token), "") =>
          List(
            StacLink(
              href =
                s"$apiHost/search?limit=$limit&next=${PaginationToken.encPaginationToken(token)}",
              rel = StacLinkType.Next,
              _type = Some(`application/json`),
              title = None
            )
          )
        case (Some(token), query) =>
          List(
            StacLink(
              href =
                s"$apiHost/search?limit=$limit&next=${PaginationToken.encPaginationToken(token)}&$query",
              rel = StacLinkType.Next,
              _type = Some(`application/json`),
              title = None
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
    val itemExtent        = Projected(item.geometry.getEnvelope, 4326)

    val insertFragment = fr"""
      INSERT INTO collection_items (id, extent, geom, item)
      VALUES
      (${item.id}, $itemExtent, $projectedGeometry, $item)
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
    val itemExtent = Projected(item.geometry.getEnvelope, 4326)
    val fragment   = fr"""
      UPDATE collection_items
      SET
        extent = $itemExtent,
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
        // this painful type inference failure thanks to subtyping will someday
        // drive me insane, but not yet
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
  ): ConnectionIO[Either[StacItemDaoError, StacItem]] =
    (for {
      itemInDB <- EitherT.fromOptionF[ConnectionIO, StacItemDaoError, StacItem](
        getCollectionItem(collectionId, itemId),
        ItemNotFound: StacItemDaoError
      )
      etagInDb = itemInDB.##
      patched  = itemInDB.asJson.deepMerge(jsonPatch).dropNullValues
      decoded  = patched.as[StacItem]
      update <- (decoded, etagInDb.toString == etag) match {
        case (Right(patchedItem), true) =>
          EitherT {
            doUpdate(itemId, patchedItem.copy(properties = patchedItem.properties.filter({
              case (_, v) => !v.isNull
            }))).attempt
          } leftMap { _ => UpdateFailed: StacItemDaoError }
        case (_, false) =>
          EitherT.leftT[ConnectionIO, StacItem] { StaleObject: StacItemDaoError }
        case (Left(err), _) =>
          EitherT.leftT[ConnectionIO, StacItem] { PatchInvalidatesItem(err): StacItemDaoError }
      }
    } yield update).value
}
