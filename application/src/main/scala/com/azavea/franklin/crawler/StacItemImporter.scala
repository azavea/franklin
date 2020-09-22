package com.azavea.franklin.crawler

import cats.data.EitherT
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.database.StacItemDao
import com.azavea.stac4s._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

class StacItemImporter(val collectionId: String, val itemUris: NonEmptyList[String])(
    implicit contextShift: ContextShift[IO]
) {

  implicit def logger = Slf4jLogger.getLogger[IO]

  private def getCollection(xa: Transactor[IO]): EitherT[IO, String, StacCollection] =
    EitherT {
      StacCollectionDao.getCollectionUnique(collectionId).transact(xa).map { collectionOption =>
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
        val amountInserted = StacItemDao.insertManyStacItems(collectionWrapper.items).transact(xa)
        EitherT.right[String](amountInserted)
      }
    } yield {
      stacItems
    }
  }.value
}
