package com.azavea.franklin.crawler

import cats.data.EitherT
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.database.StacItemDao
import com.azavea.stac4s._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

class StacItemImporter(val collectionId: String, val itemUris: NonEmptyList[String]) {

  implicit def logger = Slf4jLogger.getLogger[IO]

  private def getCollection(xa: Transactor[IO]): EitherT[IO, String, StacCollection] =
    EitherT {
      StacCollectionDao.getCollectionUnique(collectionId).transact(xa).map { collectionOption =>
        Either
          .fromOption(collectionOption, s"Could not read collection: $collectionId from database")
      }
    }

  private def readItems(collection: StacCollection): EitherT[IO, String, NonEmptyList[StacItem]] = {
    EitherT.right(itemUris.traverse(uri => StacIO.readItem(uri, true, collection)))
  }

  def runIO(xa: Transactor[IO]): IO[Either[String, NonEmptyList[StacItem]]] = {
    for {
      collection <- getCollection(xa)
      itemList   <- readItems(collection)
      stacItems <- EitherT.right[String](
        itemList
          .traverse(item =>
            StacItemDao.insertStacItem(item.copy(collection = Some(collectionId))).transact(xa)
          )
      )
    } yield {
      stacItems
    }
  }.value
}
