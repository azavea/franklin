package com.azavea.franklin.crawler

import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.azavea.stac4s.StacCollection
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.StacLinkType
import geotrellis.store.s3.AmazonS3URI
import io.circe.Decoder
import io.circe.parser.decode

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object StacIO {

  val s3 = AmazonS3ClientBuilder
    .standard()
    .withForceGlobalBucketAccessEnabled(true)
    .build()

  private def readFromLocalPath(path: String): IO[List[String]] = {
    IO(scala.io.Source.fromFile(path).getLines.toList)
  }

  private def readFromS3(path: String): IO[List[String]] = {
    val awsURI        = new AmazonS3URI(path)
    val inputStreamIO = IO(s3.getObject(awsURI.getBucket, awsURI.getKey).getObjectContent)
    inputStreamIO.map(is => scala.io.Source.fromInputStream(is).getLines().toList)
  }

  def readLinesFromPath(path: String): IO[List[String]] = {
    if (path.startsWith("s3://")) {
      readFromS3(path)
    } else {
      readFromLocalPath(path)
    }
  }

  def readJsonFromPath[T: Decoder](path: String): IO[T] = {
    val str = readLinesFromPath(path)

    str.flatMap { s =>
      decode[T](s.mkString) match {
        case Left(e)  => IO.raiseError(e)
        case Right(t) => IO.pure(t)
      }
    }
  }

  private def getPrefix(absPath: String): String = absPath.split("/").dropRight(1).mkString("/")

  def makeAbsPath(from: String, relPath: String): String = {
    // don't try to relativize links that start with s3 -- the string splitting
    // does _weird_ stuff :(
    if (relPath.startsWith("s3://")) {
      relPath
    } else {
      val prefix       = getPrefix(from)
      val prefixSplit  = prefix.split("/")
      val relPathSplit = relPath.split("/")
      val up           = relPathSplit.count(_ == "..")
      // safe because String.split always returns an array with an element
      (prefixSplit.dropRight(up) :+ (if (up > 0) relPathSplit.drop(up)
                                     else relPathSplit.drop(1)).mkString("/")) mkString ("/")
    }
  }

  def readItem(
      path: String,
      rewriteSourceIfPresent: Boolean,
      inCollection: StacCollection
  ): IO[StacItem] = {
    val readIO = readJsonFromPath[StacItem](path)
    if (!rewriteSourceIfPresent) readIO
    else {
      for {
        item <- readIO
        sourceLinkO = item.links.find(_.rel === StacLinkType.Source)
        sourceItemO <- sourceLinkO traverse { link =>
          val sourcePath = makeAbsPath(path, link.href)
          readItem(sourcePath, rewriteSourceIfPresent = false, inCollection)
        }
      } yield {
        (sourceLinkO, sourceItemO, sourceItemO flatMap { _.collection }).tupled map {
          case (link, sourceItem, collectionId) =>
            val encodedSourceItemId =
              URLEncoder.encode(sourceItem.id, StandardCharsets.UTF_8.toString)
            val encodedCollectionId =
              URLEncoder.encode(collectionId, StandardCharsets.UTF_8.toString)
            val newSourceLink =
              link.copy(href = s"/collections/$encodedCollectionId/items/$encodedSourceItemId")
            item.copy(links = newSourceLink :: item.links.filter(_.rel != StacLinkType.Source))
        } getOrElse { item }
      }
    }
  }
}
