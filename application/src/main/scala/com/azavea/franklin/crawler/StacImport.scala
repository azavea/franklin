package com.azavea.franklin.crawler

import java.nio.file.Paths

import cats.{Applicative, MonadError}
import com.azavea.franklin.database.{StacCollectionDao, StacItemDao}
import doobie.ConnectionIO
import doobie.implicits._
import geotrellis.server.stac._
import io.circe.Json
import io.circe.fs2._

import scala.concurrent.ExecutionContext.Implicits.global

class StacImport(val catalogRoot: String) {

  def addSelf(collection: StacCollection, absPath: String): StacCollection = {
    if (collection.links.filter(_.rel == Self).isEmpty) {
      collection.copy(
        links = collection.links :+ StacLink(
          absPath,
          Self,
          Some(`application/json`),
          collection.title,
          List.empty
        )
      )
    } else collection
  }

  def insertCollection(
      collection: StacCollection,
      parentCollection: Option[StacCollection],
      catalog: StacCatalog
  ): ConnectionIO[StacCollection] =
    Applicative[ConnectionIO].pure {
      println(
        s"Inserting collection ${collection.title} with parent ${parentCollection map { _.title }} into catalog ${catalog.title}"
      )
    } flatMap { _ =>
      StacCollectionDao.insertStacCollection(collection, parentCollection map { _.id })
    }

  private def getPrefix(absPath: String): String = absPath.split("/").dropRight(1).mkString("/")

  private def makeAbsPath(from: String, relPath: String): String = {
    val prefix       = getPrefix(from)
    val prefixSplit  = prefix.split("/")
    val relPathSplit = relPath.split("/")
    val up           = relPathSplit.filter(_ == "..").size
    // safe because String.split always returns an array with an element
    (prefixSplit.dropRight(up) :+ (if (up > 0) relPathSplit.drop(up)
                                   else relPathSplit.drop(1)).mkString("/")) mkString ("/")
  }

  def readFromS3(path: String): fs2.Stream[ConnectionIO, Json] = ???

  def readFromLocalPath(path: String): fs2.Stream[ConnectionIO, Json] =
    fs2.io.file
      .readAll[ConnectionIO](
        Paths.get(path),
        global,
        256
      )
      .through(byteArrayParser[ConnectionIO])

  def readPath(path: String): fs2.Stream[ConnectionIO, Json] =
    if (path.startsWith("s3://")) {
      readFromS3(path)
    } else {
      readFromLocalPath(path)
    }

  def readRoot: fs2.Stream[ConnectionIO, StacCatalog] = {
    readPath(catalogRoot).through(decoder[ConnectionIO, StacCatalog])
  }

  def readCollection(
      path: String,
      parent: Option[StacCollection]
  ): fs2.Stream[ConnectionIO, (StacCollection, Option[StacCollection])] =
    readPath(path).through(decoder[ConnectionIO, StacCollection]) flatMap { collection =>
      val children = collection.links.filter(_.rel == Child)
      if (children.isEmpty) {
        fs2.Stream.emit((addSelf(collection, path), None)).covary[ConnectionIO]
      } else {
        fs2.Stream
          .emit((collection, parent))
          .covary[ConnectionIO] ++ (fs2.Stream.emits(children) flatMap { child =>
          readCollection(makeAbsPath(path, child.href), Some(collection))
        })
      }
    }

  def readItem(path: String): fs2.Stream[ConnectionIO, StacItem] =
    readPath(path).through(decoder[ConnectionIO, StacItem])

  def readChildren(
      catalog: StacCatalog
  ): fs2.Stream[ConnectionIO, (StacCollection, Option[StacCollection])] = {
    val children = catalog.links.filter(link => link.rel == Child)
    fs2.Stream.emits(children).covary[ConnectionIO] flatMap { child =>
      readCollection(makeAbsPath(catalogRoot, child.href), None)
    }
  }

  def readItems(
      collection: StacCollection
  ): fs2.Stream[ConnectionIO, StacItem] = {
    val collectionAbsPath: ConnectionIO[String] = collection.links
      .filter(link => link.rel == Self)
      .map((link: StacLink) => link.href)
      .headOption match {
      case Some(p) => Applicative[ConnectionIO].pure(p)
      case _ =>
        MonadError[ConnectionIO, Throwable].raiseError(
          new Exception("No self link on collection, unable to derive item absolute path")
        )
    }

    val items = collection.links.filter(link => link.rel == Item)
    for {
      absPath <- fs2.Stream.eval { collectionAbsPath }
      result <- fs2.Stream.emits(items).covary[ConnectionIO] flatMap { item =>
        readItem(makeAbsPath(absPath, item.href))
      }
    } yield result
  }

  def run(): fs2.Stream[ConnectionIO, Unit] =
    for {
      catalog              <- readRoot
      (collection, parent) <- readChildren(catalog)
      _                    <- fs2.Stream.eval { insertCollection(collection, parent, catalog) }
      item                 <- readItems(collection)
      _                    <- fs2.Stream.eval { StacItemDao.insertStacItem(item) }
    } yield ()
}
