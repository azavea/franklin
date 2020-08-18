package com.azavea.franklin.crawler

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.azavea.stac4s.StacCollection
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.StacLinkType
import geotrellis.store.s3.AmazonS3URI
import io.chrisdavenport.log4cats.Logger
import io.circe.{Decoder, DecodingFailure, Error => CirceError, ParsingFailure, CursorOp}
import io.circe.parser.decode
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.circe._
import sttp.model.{Uri => SttpUri}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object StacIO {

  private def logCirceError(path: String, error: CirceError)(
      implicit logger: Logger[IO]
  ): IO[Unit] =
    error match {
      case ParsingFailure(message, _) =>
        logger.error(s"Value at $path was not valid json: $message")
      case DecodingFailure("Attempt to decode value on failed cursor", history) =>
        logger.error(
          s"In $path, I expected to find a value at ${CursorOp.opsToPath(history)}, but there was nothing 😞"
        )
      case DecodingFailure(s, history) =>
        logger.error(
          s"In $path, I found something unexpected at ${CursorOp.opsToPath(history)}. I expected a value of type $s 🤔"
        )
    }

  private def logBadResponse(path: String, code: Int)(implicit logger: Logger[IO]): IO[Unit] =
    logger.error(s"The server responsible for $path rejected my request with a status of $code")

  val s3 = AmazonS3ClientBuilder
    .standard()
    .withForceGlobalBucketAccessEnabled(true)
    .build()

  private def readFromLocalPath(path: String): IO[List[String]] = {
    IO(scala.io.Source.fromFile(path).getLines.toList)
  }

  private def readJsonFromS3(path: String): IO[List[String]] = {
    val awsURI        = new AmazonS3URI(path)
    val inputStreamIO = IO(s3.getObject(awsURI.getBucket, awsURI.getKey).getObjectContent)
    inputStreamIO.map(is => scala.io.Source.fromInputStream(is).getLines().toList)
  }

  def readLinesFromPath(path: String): IO[List[String]] = {
    if (path.startsWith("s3://")) {
      readJsonFromS3(path)
    } else {
      readFromLocalPath(path)
    }
  }

  def readJsonFromPath[T: Decoder](
      path: String
  )(implicit contextShift: ContextShift[IO], logger: Logger[IO]): IO[T] = {
    if (path.startsWith("http")) {
      {
        readJsonFromHttp(path)
      }
    } else {
      val str = readLinesFromPath(path)

      str.flatMap { s =>
        decode[T](s.mkString) match {
          case Left(e)  => logCirceError(path, e) *> IO.raiseError(e)
          case Right(t) => IO.pure(t)
        }
      }
    }
  }

  def readJsonFromHttp[T: Decoder](
      url: String
  )(implicit contextShift: ContextShift[IO], logger: Logger[IO]): IO[T] =
    AsyncHttpClientCatsBackend[IO]().flatMap { implicit backend =>
      for {
        response <- basicRequest.get(uri"$url").response(asJson[T]).send[IO]
        decoded <- response.body match {
          case Left(e @ DeserializationError(_, err)) =>
            logCirceError(s"$url", err) *> IO.raiseError(e)
          case Left(e @ HttpError(_, code)) =>
            logBadResponse(s"$url", code.code) *> IO.raiseError(e)
          case Right(body) => IO.pure(body)
        }
      } yield decoded
    }

  private def getPrefix(absPath: String): String = absPath.split("/").dropRight(1).mkString("/")

  def makeAbsPath(from: String, relPath: String): String = {
    // don't try to relativize links that start with s3 -- the string splitting
    // does _weird_ stuff :(
    if (relPath.startsWith("s3://") || relPath.startsWith("http")) {
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
  )(implicit contextShift: ContextShift[IO], logger: Logger[IO]): IO[StacItem] = {
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
