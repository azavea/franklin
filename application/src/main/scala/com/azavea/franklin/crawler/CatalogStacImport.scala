package com.azavea.franklin.crawler

import cats.data.{StateT, Validated, ValidatedNel}
import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import com.azavea.franklin.crawler.StacIO._
import com.azavea.franklin.database.{StacCollectionDao, StacItemDao}
import com.azavea.stac4s.StacLinkType._
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.label.LabelItemExtension
import com.azavea.stac4s.syntax._
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.io.json._
import geotrellis.vector.{Feature, Geometry}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Error => CirceError, JsonObject}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class CatalogStacImport(val catalogRoot: String) {

  implicit val cs     = IO.contextShift(global)
  implicit def logger = Slf4jLogger.getLogger[IO]

  private def getSelfLink(collection: StacCollection): StacLink = {
    val encodedCollectionId =
      URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
    StacLink(
      s"/collections/$encodedCollectionId",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )
  }

  private def getItemsLink(collection: StacCollection): StacLink = {
    val encodedCollectionId =
      URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
    StacLink(
      s"/collections/$encodedCollectionId/items",
      StacLinkType.Items,
      Some(`application/json`),
      None
    )
  }

  def readCollection(path: String, xa: Transactor[IO])(
      implicit backend: SttpBackend[IO, Nothing, NothingT]
  ): IO[ValidatedNel[CirceError, StacCollection]] =
    readJsonFromPath[StacCollection](path) flatMap {
      case Validated.Valid(collection) =>
        StacCollectionDao
          .insertStacCollection(
            collection.copy(
              links =
                getItemsLink(collection) +: getSelfLink(collection) +: filterLinks(collection.links)
            ),
            None
          )
          .transact(xa) map { _ => collection.valid }
      case errs @ Validated.Invalid(_) => IO.pure(errs)
    }

  def readRoot(
      xa: Transactor[IO]
  )(implicit backend: SttpBackend[IO, Nothing, NothingT]): IO[String] = {
    for {
      collectionResult <- readCollection(catalogRoot, xa) map { _.map(_.id) }
      catalogResult    <- readJsonFromPath[StacCatalog](catalogRoot) map { _.map(_.id) }
      rootId           <- StacIO.collectionCatalogFallback(catalogRoot, collectionResult, catalogResult)
    } yield rootId
  }

  private def createCollectionForGeoJsonAsset(
      forItem: StacItem,
      fromPath: String
  )(
      implicit backend: SttpBackend[IO, Nothing, NothingT]
  ): IO[List[(Map[String, StacAsset], CollectionWrapper)]] = {
    val geojsonAssets = forItem.assets.toList.filter {
      case (_, asset) => asset._type === Some(`application/geo+json`)
    }
    (geojsonAssets.nonEmpty, forItem.getExtensionFields[LabelItemExtension], forItem.collection) match {
      case (true, Validated.Invalid(errs), _) =>
        logger.warn(
          s"""$forItem is not a valid label item, so skipping geojson import.\n${errs.toList
            .mkString("\n")}"""
        ) map { _ => List.empty }
      case (_, _, None) =>
        logger.warn(
          s"Item ${forItem.id} did not have a collection, so skipping geojson collection creation"
        ) map { _ => List.empty }
      case (_, _, Some(collectionId)) =>
        val encodedCollectionId = URLEncoder.encode(collectionId, StandardCharsets.UTF_8.toString)
        geojsonAssets.zipWithIndex traverse {
          case ((assetKey, asset), idx) =>
            readJsonFromPath[JsonFeatureCollection](makeAbsPath(fromPath, asset.href)) flatMap {
              case Validated.Valid(featureCollection) =>
                logger.debug(
                  s"Item ${forItem.id} has a geojson asset, so creating a collection for its features"
                ) map { _ =>
                  val parentCollectionHref =
                    s"/collections/$encodedCollectionId"
                  val derivedFromItemHref =
                    s"$parentCollectionHref/items/${URLEncoder.encode(forItem.id, StandardCharsets.UTF_8.toString)}"
                  val parentCollectionLink = StacLink(
                    parentCollectionHref,
                    StacLinkType.Collection,
                    Some(`application/json`),
                    None
                  )
                  val derivedFromItemLink =
                    StacLink(
                      derivedFromItemHref,
                      StacLinkType.VendorLinkType("derived_from"),
                      Some(`application/json`),
                      None
                    )
                  val labelCollection = StacCollection(
                    "Collection",
                    "1.0.0-rc2",
                    Nil,
                    s"${forItem.id}-labels-${idx + 1}",
                    Some(s"${forItem.id} Labels"),
                    s"Labels for ${forItem.id}'s ${assetKey} asset",
                    Nil,
                    Proprietary(),
                    Nil,
                    StacExtent(
                      SpatialExtent(List(forItem.bbox)),
                      Interval(
                        List(
                          TemporalExtent(
                            forItem.properties.asJson.hcursor
                              .get[Instant]("datetime")
                              .getOrElse(Instant.now),
                            None
                          )
                        )
                      )
                    ),
                    Map.empty,
                    forItem.properties.asJson.asObject.getOrElse(JsonObject.empty),
                    List(parentCollectionLink, derivedFromItemLink),
                    Some(Map.empty)
                  )
                  val featureItems =
                    featureCollection.getAllFeatures[Feature[Geometry, JsonObject]] map { feature =>
                      FeatureExtractor.toItem(
                        feature,
                        forItem,
                        encodedCollectionId,
                        labelCollection
                      )
                    }

                  val newAsset = Map(
                    s"Label collection ${idx + 1}" -> StacAsset(
                      s"/collections/${URLEncoder
                        .encode(labelCollection.id, StandardCharsets.UTF_8.toString)}",
                      None,
                      Some("Collection containing items for this item's label geojson asset"),
                      Set(StacAssetRole.VendorAsset("data-collection")),
                      Some(`application/json`)
                    )
                  )
                  (
                    newAsset,
                    CollectionWrapper(
                      labelCollection,
                      None,
                      Nil,
                      featureItems.toList
                    )
                  )
                }
              case Validated.Invalid(errs) =>
                (errs traverse { err =>
                  StacIO.logCirceError(makeAbsPath(fromPath, asset.href), err)
                }) *>
                  IO.raiseError(errs.head)
            }
        }
    }
  }

  private def insertCollection(
      collection: CollectionWrapper
  ): ConnectionIO[StacCollection] = {
    for {
      colInsert <- StacCollectionDao.insertStacCollection(
        collection.value.copy(links =
          getItemsLink(collection.value) +: getSelfLink(collection.value) +: collection.value.links
        ),
        collection.parent.map(_.value.id)
      )
      _ <- collection.items.traverse(item => StacItemDao.insertStacItem(item))
      _ <- collection.children.traverse(child => insertCollection(child))
    } yield colInsert
  }

  def allChildPaths(
      xa: Transactor[IO]
  )(implicit backend: SttpBackend[IO, Nothing, NothingT]): StateT[IO, String, List[String]] =
    StateT[IO, String, List[String]] { (root: String) =>
      for {
        collectionLinks <- readCollection(root, xa) map { _.map(_.links) }
        catalogLinks    <- readJsonFromPath[StacCatalog](root) map { _.map(_.links) }
        rootLinks       <- StacIO.collectionCatalogFallback(root, collectionLinks, catalogLinks)
        children      = rootLinks.filter(_.rel == StacLinkType.Child)
        childAbsHrefs = children map { link => makeAbsPath(root, link.href) }
        _ <- logger.debug(s"Child hrefs for $root: $childAbsHrefs")
        childHrefs <- childAbsHrefs match {
          case Nil => IO.pure((root, List(root)))
          case refs =>
            refs traverse { (ref: String) =>
              allChildPaths(xa).runA(ref)
            } map { childRefs => (root, root :: childRefs.flatten) }
        }
      } yield childHrefs
    }

  def insertItemsForAbsHref(absHref: String, xa: Transactor[IO])(
      implicit backend: SttpBackend[IO, Nothing, NothingT]
  ): IO[Unit] = {
    val collectionAttemptIO = readCollection(absHref, xa) map { collectionValidated =>
      collectionValidated.map { collection => (collection.links, Option(collection.id)) }
    }
    val catalogAttemptIO = readJsonFromPath[StacCatalog](absHref) map { catalogValidated =>
      catalogValidated map { catalog => (catalog.links, None) }
    }
    (collectionAttemptIO, catalogAttemptIO).tupled flatMap {
      case (collectionAttempt, catalogAttempt) =>
        StacIO.collectionCatalogFallback(absHref, collectionAttempt, catalogAttempt) flatMap {
          case (links, collectionO) =>
            val filtered = links.filter(_.rel == StacLinkType.Item)
            logger
              .debug(s"Links to read for $absHref: ${filtered map { _.href }}") <* (filtered traverse {
              link =>
                val itemPath = makeAbsPath(absHref, link.href)
                readItem(itemPath, true, collectionO) flatMap { item =>
                  logger
                    .debug(s"Item links after read for ${item.id}: ${item.links map { _.href }}") *>
                    createCollectionForGeoJsonAsset(
                      item,
                      itemPath
                    ) flatMap { newAssets =>
                    val assets   = newAssets map { _._1 }
                    val wrappers = newAssets map { _._2 }
                    (wrappers traverse { collectionWrapper =>
                      insertCollection(collectionWrapper).transact(xa)
                    }) <*
                      logger.debug(s"Item ${item.id} has ${newAssets.length} additional assets") *>
                        logger.debug(s"Item links before write: ${item.links map { _.href }}") *>
                        ((
                          StacItemDao
                            .insertStacItem(
                              item
                                .copy(
                                  assets =
                                    item.assets ++ assets.foldK
                                )
                            )
                            .transact(xa)
                          ))
                  }
                }
            })
        }
    }
  }

  def runIO(xa: Transactor[IO])(implicit backend: SttpBackend[IO, Nothing, NothingT]): IO[Unit] =
    for {
      _             <- readRoot(xa)
      allChildHrefs <- allChildPaths(xa).runA(catalogRoot)
      _             <- logger.debug(s"All children: $allChildHrefs")
      _             <- allChildHrefs traverse { href => insertItemsForAbsHref(href, xa) }
    } yield ()
}
