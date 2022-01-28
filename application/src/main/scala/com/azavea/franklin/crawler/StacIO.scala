package com.azavea.franklin.crawler

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.azavea.stac4s.{`application/json`, StacCollection, StacItem, StacLink, StacLinkType}
import geotrellis.store.s3.AmazonS3URI
import io.chrisdavenport.log4cats.Logger
import io.circe.parser.decodeAccumulating
import io.circe.{CursorOp, Decoder, DecodingFailure, Error => CirceError, ParsingFailure}
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.circe._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object StacIO {

  def collectionCatalogFallback[T](
      path: String,
      collectionAttempt: ValidatedNel[CirceError, T],
      catalogAttempt: ValidatedNel[CirceError, T]
  )(implicit logger: Logger[IO]) =
    (collectionAttempt, catalogAttempt) match {
      case (Validated.Valid(s), _) => IO.pure(s)
      case (_, Validated.Valid(s)) => IO.pure(s)
      case (Validated.Invalid(collErrs), Validated.Invalid(catalogErrs)) =>
        StacIO.logFailedCollectionCatalogFallback(path, collErrs, catalogErrs)
    }

  def logFailedCollectionCatalogFallback[T](
      path: String,
      collectionErrs: NonEmptyList[CirceError],
      catalogErrs: NonEmptyList[CirceError]
  )(implicit logger: Logger[IO]): IO[T] =
    logger.error(s"Could not read $path as either a collection or a catalog") *>
      logger.error("Collection errors:") *> (collectionErrs traverse {
      StacIO.logCirceError(path, _)
    }) *> logger.error("Catalog errors:") *> (catalogErrs traverse {
      StacIO.logCirceError(path, _)
    }) *> IO.raiseError(collectionErrs.head)

  def logCirceError(path: String, error: CirceError)(
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

  lazy val s3 = AmazonS3ClientBuilder
    .standard()
    .withForceGlobalBucketAccessEnabled(true)
    .build()

  private def readFromLocalPath(path: String): IO[List[String]] = {
    IO(scala.io.Source.fromFile(path).getLines.toList)
  }

  private def encodeString(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

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
  )(
      implicit backend: SttpBackend[IO, Nothing, NothingT]
  ): IO[ValidatedNel[CirceError, T]] = {
    if (path.startsWith("http")) {
      {
        readJsonFromHttp(path)
      }
    } else {
      val str = readLinesFromPath(path)

      str.flatMap { s =>
        IO.pure(
          decodeAccumulating[T](s.mkString)
        )
      }
    }
  }

  def readJsonFromHttp[T: Decoder](
      url: String
  )(
      implicit backend: SttpBackend[IO, Nothing, NothingT]
  ): IO[ValidatedNel[CirceError, T]] =
    for {
      response <- basicRequest.get(uri"$url").response(asJson[T]).send[IO]
    } yield {
      response.body match {
        case Left(DeserializationError(_, err)) =>
          Validated.Invalid(NonEmptyList.of(err))
        case Left(e @ HttpError(_, code)) =>
          Validated.Invalid(
            NonEmptyList.of(
              ParsingFailure(s"Something went wrong reading json from $url with code $code", e)
            )
          )
        case Right(body) => Validated.Valid(body)
      }
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
      inCollectionId: Option[String]
  )(
      implicit
      logger: Logger[IO],
      backend: SttpBackend[IO, Nothing, NothingT]
  ): IO[StacItem] = {
    val readIO = readJsonFromPath[StacItem](path)
    val preLog =
      if (!rewriteSourceIfPresent)(logger.debug(s"Not rewriting source link at $path"))
      else IO.unit
    preLog *> {
      for {
        itemParseResult <- readIO
        item <- itemParseResult match {
          case Validated.Valid(item) => IO.pure(item)
          case Validated.Invalid(errs) =>
            (errs traverse { err =>
              logCirceError(path, err)
            }) *> IO.raiseError(errs.head)
        }
        _ <- logger.debug(s"Rewriting item source link for item ${item.id}")
        sourceLinkO = item.links.find(_.rel === StacLinkType.Source)
        sourceItemO <- sourceLinkO traverse { link =>
          val sourcePath = makeAbsPath(path, link.href)
          readItem(sourcePath, rewriteSourceIfPresent = false, inCollectionId)
        }
      } yield {
        val collectionIdO = item.collection orElse inCollectionId orElse (sourceItemO flatMap {
          _.collection
        })

        val sourceLink =
          (sourceLinkO, sourceItemO, sourceItemO flatMap { _.collection }).tupled map {
            case (link, sourceItem, collectionId) =>
              val encodedSourceItemId =
                encodeString(sourceItem.id)
              val encodedCollectionId =
                encodeString(collectionId)
              link.copy(href = s"/collections/$encodedCollectionId/items/$encodedSourceItemId")
          }

        val collectionAndSelfLink = collectionIdO map { collectionId =>
          val encodedCollectionId =
            encodeString(collectionId)
          List(
            StacLink(
              s"/collections/${encodedCollectionId}",
              StacLinkType.Collection,
              Some(`application/json`),
              None
            ),
            StacLink(
              s"/collections/${encodedCollectionId}/items/${encodeString(item.id)}",
              StacLinkType.Self,
              Some(`application/json`),
              None
            )
          )
        }

        item.copy(links =
          sourceLink.toList ++ (collectionAndSelfLink getOrElse Nil) ++ filterLinks(
            item.links.filter(_.rel != StacLinkType.Source)
          )
        )

      }
    }
  }
}
