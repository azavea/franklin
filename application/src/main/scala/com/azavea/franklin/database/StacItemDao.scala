package com.azavea.franklin.database

import cats.data.{EitherT, OptionT}
import cats.syntax.all._
import com.azavea.franklin.datamodel.{
  BulkExtent,
  Context,
  PaginationToken,
  SearchMethod,
  StacSearchCollection
}
import com.azavea.franklin.extensions.paging.PagingLinkExtension
import com.azavea.stac4s._
import com.azavea.stac4s.syntax._
import doobie.Fragment
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.refined.implicits._
import doobie.util.update.Update
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Geometry, Projected}
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
    fr"collection = $collectionId"
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

  def getSearchLinks(
      items: List[StacItem],
      limit: NonNegInt,
      searchFilters: SearchFilters,
      apiHost: NonEmptyString,
      searchMethod: SearchMethod
  ): ConnectionIO[List[StacLink]] =
    for {
      nextToken <- items.lastOption traverse { item =>
        getPaginationToken(item.id)
      } map { _.flatten }
      nextExists <- nextToken traverse { token =>
        query.filter(searchFilters.copy(next = Some(token), limit = Some(1))).exists
      }
    } yield {
      (nextExists, nextToken, searchMethod, searchFilters.asQueryParameters) match {
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
    }

  def getSearchContext(
      searchFilters: SearchFilters
  ): ConnectionIO[Int] = query.filter(searchFilters.copy(next = None)).count

  // This is only used to make the bulk insert happy and make the number of parameters line up
  private case class StacItemBulkImport(
      id: String,
      geom: Projected[Geometry],
      item: StacItem,
      collection: Option[String]
  )

  def insertManyStacItems(items: List[StacItem]): ConnectionIO[Int] = {
    val bulkExtent = items.foldLeft(BulkExtent(None, None, None))({
      case (BulkExtent(start, end, bbox), item) => {
        val itemDt   = item.properties.asJson.hcursor.downField("datetime").as[Instant].toOption
        val itemBbox = item.bbox
        val newBbox  = bbox.map(box => box.union(itemBbox)) getOrElse itemBbox
        val newEndpoints = itemDt flatMap { newDt =>
          (start map { dt =>
            if (dt.isBefore(newDt)) { dt }
            else newDt
          } orElse Some(newDt), end map { dt =>
            if (dt.isAfter(newDt)) dt else newDt
          } orElse Some(newDt)).tupled
        }

        newEndpoints match {
          case Some((newStart, newEnd)) =>
            ExtentUpdate(Some(newStart), Some(newEnd), Some(newBbox))
          case None => ExtentUpdate(start, end, Some(newBbox))
        }

      }
    })
    val insertFragment = """
      INSERT INTO collection_items (id, geom, item, collection)
      VALUES
      (?, ?, ?, ?)
      """
    val stacItemInserts =
      items.map(i => StacItemBulkImport(i.id, Projected(i.geometry, 4326), i, i.collection))
    Update[StacItemBulkImport](insertFragment).updateMany(stacItemInserts)
  }

  def insertStacItem(item: StacItem): ConnectionIO[StacItem] = {
    val projectedGeometry = Projected(item.geometry, 4326)

    val insertFragment = fr"""
      INSERT INTO collection_items (id, geom, item, collection)
      VALUES
      (${item.id}, $projectedGeometry, $item, ${item.collection})
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
    val fragment = fr"""
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
