package com.azavea.franklin.crawler

import cats.data.EitherT
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all._
import com.azavea.franklin.database.{getItemsBulkExtent, StacCollectionDao, StacItemDao}
import com.azavea.stac4s._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import sttp.client.{NothingT, SttpBackend}

class StacItemImporter(val collectionId: String, val itemUris: NonEmptyList[String])(
    implicit contextShift: ContextShift[IO],
    backend: SttpBackend[IO, Nothing, NothingT]
) {

  implicit def logger = Slf4jLogger.getLogger[IO]

  private def getCollection(xa: Transactor[IO]): EitherT[IO, String, StacCollection] =
    EitherT {
      StacCollectionDao.getCollection(collectionId).transact(xa).map { collectionOption =>
        Either
          .fromOption(collectionOption, s"Could not read collection: $collectionId from database")
      }
    }

  private def readItems(
      collection: StacCollection
  ): EitherT[IO, String, NonEmptyList[StacItem]] = {
    EitherT.right(itemUris.traverse(uri => StacIO.readItem(uri, true, Some(collection.id))))
  }

  def runIO(xa: Transactor[IO]): IO[Either[String, Int]] = {
    for {
      collection <- getCollection(xa)
      itemList   <- readItems(collection)
      stacItems <- {
        val collectionWrapper = CollectionWrapper(
          collection.copy(id = collectionId),
          None,
          List.empty,
          itemList.toList
        ).updateLinks
        val insertResult = (
          StacItemDao.insertManyStacItems(collectionWrapper.items, collection)
            <* (collectionWrapper.items.toNel traverse { itemNel =>
              StacCollectionDao.updateExtent(
                collectionId,
                getItemsBulkExtent(itemNel)
              )
            })
        ).transact(xa)
        EitherT.right[String](insertResult flatMap {
          case (ids, n) if ids.isEmpty => n.pure[IO]
          case (ids, n) =>
            logger.warn(
              s"Completed import but with errors. You can try adding these items individually:\n${ids
                .mkString("\n")}"
            ) map { _ => n }
        })
      }
    } yield {
      stacItems
    }
  }.value
}
