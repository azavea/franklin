package com.azavea.franklin.crawler

import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import com.azavea.franklin.database.{StacCollectionDao, StacItemDao}
import com.azavea.stac4s.StacLinkType._
import com.azavea.stac4s._
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.types.string.NonEmptyString
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.parser.decode

import scala.concurrent.ExecutionContext.Implicits.global

class StacImport(val catalogRoot: String, serverHost: NonEmptyString) {

  implicit val cs     = IO.contextShift(global)
  implicit def logger = Slf4jLogger.getLogger[IO]

  val s3 = AmazonS3ClientBuilder
    .standard()
    .withForceGlobalBucketAccessEnabled(true)
    .build()

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

  private def readFromS3(path: String): IO[String] = {
    val awsURI        = new AmazonS3URI(path)
    val inputStreamIO = IO(s3.getObject(awsURI.getBucket, awsURI.getKey).getObjectContent)
    inputStreamIO.map(is => scala.io.Source.fromInputStream(is).mkString)
  }

  private def readFromLocalPath(path: String): IO[String] = {
    IO(scala.io.Source.fromFile(path).getLines.mkString)
  }

  private def readPath[T: Decoder](path: String): IO[T] = {
    val str = if (path.startsWith("s3://")) {
      readFromS3(path)
    } else {
      readFromLocalPath(path)
    }

    str.flatMap { s =>
      decode[T](s) match {
        case Left(e)  => IO.raiseError(e)
        case Right(t) => IO.pure(t)
      }
    }
  }

  private def readRoot: IO[StacCatalog] = readPath[StacCatalog](catalogRoot)

  private def readItem(path: String): IO[StacItem] = readPath[StacItem](path)

  private def insertCollection(
      collection: CollectionWrapper
  ): ConnectionIO[StacCollection] = {
    for {
      colInsert <- StacCollectionDao.insertStacCollection(
        collection.value,
        collection.parent.map(_.value.id)
      )
      _ <- collection.items.traverse(item => StacItemDao.insertStacItem(item))
      _ <- collection.children.traverse(child => insertCollection(child))
    } yield colInsert
  }

  private def readCollectionWrapper(
      path: String,
      parent: Option[CollectionWrapper]
  ): IO[CollectionWrapper] = {
    for {
      stacCollection <- readPath[StacCollection](path)
      _              <- logger.info(s"Read STAC Collection : ${stacCollection.title}")
      itemLinks     = stacCollection.links.filter(link => link.rel == Item)
      childrenLinks = stacCollection.links.filter(link => link.rel == Child)
      children <- childrenLinks.traverse(link =>
        readCollectionWrapper(makeAbsPath(path, link.href), None)
      )
      items <- itemLinks.traverse(link => readItem(makeAbsPath(path, link.href)))
    } yield {
      val collection      = CollectionWrapper(stacCollection, parent, children, items)
      val updatedChildren = collection.children.map(child => child.copy(parent = Some(collection)))
      val updatedItems = collection.items.map { item =>
        val links = filterLinks(item.links)
        item.copy(links = links)
      }
      collection.copy(children = updatedChildren, items = updatedItems)
    }
  }

  def runIO(xa: Transactor[IO]): IO[Unit] = {
    for {
      catalog <- readRoot
      collections <- catalog.links
        .filter(_.rel == StacLinkType.Child)
        .traverse(c => readCollectionWrapper(makeAbsPath(catalogRoot, c.href), None))
      _ <- collections.traverse(c => insertCollection(c.updateLinks(serverHost))).transact(xa)
    } yield ()
  }
}
