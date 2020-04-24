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
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.io.json._
import geotrellis.vector.{Feature, Geometry}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.JsonObject
import io.circe.parser.decode

import scala.concurrent.ExecutionContext.Implicits.global

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class StacImport(val catalogRoot: String, serverHost: NonEmptyString) {

  implicit val cs     = IO.contextShift(global)
  implicit def logger = Slf4jLogger.getLogger[IO]

  val s3 = AmazonS3ClientBuilder
    .standard()
    .withForceGlobalBucketAccessEnabled(true)
    .build()

  private def getPrefix(absPath: String): String = absPath.split("/").dropRight(1).mkString("/")

  private def makeAbsPath(from: String, relPath: String): String = {
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

  private def readItem(
      path: String,
      rewriteSourceIfPresent: Boolean,
      inCollection: StacCollection
  ): IO[StacItem] = {
    val readIO = readPath[StacItem](path)
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
            val newSourceLink = link.copy(href =
              s"${serverHost.value}/collections/$encodedCollectionId/items/$encodedSourceItemId"
            )
            item.copy(links = newSourceLink :: item.links.filter(_.rel != StacLinkType.Source))
        } getOrElse { item }
      }
    }
  }

  private def createCollectionForGeoJsonAsset(
      forItem: StacItem,
      inCollection: StacCollection,
      fromPath: String
  ): IO[List[(Map[String, StacItemAsset], CollectionWrapper)]] = {
    val geojsonAssets = forItem.assets.toList.filter {
      case (_, asset) => asset._type === Some(`application/geo+json`)
    }
    geojsonAssets.zipWithIndex traverse {
      case ((assetKey, asset), idx) =>
        readPath[JsonFeatureCollection](makeAbsPath(fromPath, asset.href)) map {
          featureCollection =>
            val parentCollectionHref =
              s"$serverHost/collections/${URLEncoder.encode(inCollection.id, StandardCharsets.UTF_8.toString)}"
            val derivedFromItemHref =
              s"$parentCollectionHref/items/${URLEncoder.encode(forItem.id, StandardCharsets.UTF_8.toString)}"
            val parentCollectionLink = StacLink(
              parentCollectionHref,
              StacLinkType.Collection,
              Some(`application/json`),
              None,
              Nil
            )
            val derivedFromItemLink =
              StacLink(
                derivedFromItemHref,
                StacLinkType.VendorLinkType("derived_from"),
                Some(`application/json`),
                None,
                Nil
              )
            val labelCollection = StacCollection(
              "0.9.0",
              Nil,
              s"${forItem.id}-labels-$idx",
              Some(s"${forItem.id} Labels"),
              s"Labels for ${forItem.id}'s ${assetKey} asset",
              Nil,
              Proprietary(),
              Nil,
              inCollection.extent.copy(spatial = SpatialExtent(List(forItem.bbox))),
              forItem.properties,
              List(parentCollectionLink, derivedFromItemLink)
            )
            val featureItems = featureCollection.getAllFeatures[Feature[Geometry, JsonObject]] map {
              feature =>
                FeatureExtractor.toItem(
                  feature,
                  forItem,
                  inCollection.id,
                  labelCollection,
                  serverHost
                )
            }

            val newAsset = Map(
              s"Label collection $idx" -> StacItemAsset(
                s"$serverHost/collections/${URLEncoder
                  .encode(labelCollection.id, StandardCharsets.UTF_8.toString)}",
                None,
                Some("Collection containing items for this item's label geojson asset"),
                List(StacAssetRole.Data),
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
    }
  }

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
      _              <- logger.info(s"Read STAC Collection : ${stacCollection.title getOrElse path}")
      itemLinks     = stacCollection.links.filter(link => link.rel == Item)
      childrenLinks = stacCollection.links.filter(link => link.rel == Child)
      children <- childrenLinks.traverse(link =>
        readCollectionWrapper(makeAbsPath(path, link.href), None)
      )
      itemsWithCollections <- itemLinks.traverse(link =>
        for {
          item <- readItem(
            makeAbsPath(path, link.href),
            rewriteSourceIfPresent = true,
            inCollection = stacCollection
          )
          assetsWithCollections <- createCollectionForGeoJsonAsset(
            item,
            stacCollection,
            makeAbsPath(path, link.href)
          )
        } yield {
          val labelCollectionAssets = (assetsWithCollections map { _._1 }).foldK
          val collections           = assetsWithCollections map { _._2 }
          val updatedItem = item.copy(
            assets = item.assets ++ labelCollectionAssets
          )
          (updatedItem, collections)
        }
      )
    } yield {
      val collection =
        CollectionWrapper(stacCollection, parent, children, itemsWithCollections map { _._1 })
      val itemLabelCollections = itemsWithCollections flatMap { _._2 }
      val updatedChildren = (collection.children ++ itemLabelCollections).map(child =>
        child.copy(parent = Some(collection))
      )
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
      _       <- IO { println("Read catalog") }
      collections <- catalog.links
        .filter(_.rel == StacLinkType.Child)
        .traverse(c => readCollectionWrapper(makeAbsPath(catalogRoot, c.href), None))
      _ <- collections
        .traverse(c => {
          insertCollection(c.updateLinks(serverHost)) map { _ =>
            println(s"Inserted collection: ${c.value.id}")
          }
        })
        .transact(xa)
    } yield ()
  }
}
