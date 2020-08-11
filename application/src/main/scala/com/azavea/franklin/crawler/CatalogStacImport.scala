package com.azavea.franklin.crawler

import cats.data.Validated.Invalid
import cats.effect.IO
import cats.implicits._
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
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.io.json._
import geotrellis.vector.{Feature, Geometry}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.JsonObject
import io.circe.parser.decode
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CatalogStacImport(val catalogRoot: String) {

  implicit val cs     = IO.contextShift(global)
  implicit def logger = Slf4jLogger.getLogger[IO]

  private def readRoot: IO[StacCatalog] = readJsonFromPath[StacCatalog](catalogRoot)

  private def createCollectionForGeoJsonAsset(
      forItem: StacItem,
      inCollection: StacCollection,
      fromPath: String
  ): IO[List[(Map[String, StacItemAsset], CollectionWrapper)]] = {
    val geojsonAssets = forItem.assets.toList.filter {
      case (_, asset) => asset._type === Some(`application/geo+json`)
    }
    (geojsonAssets.nonEmpty, forItem.getExtensionFields[LabelItemExtension]) match {
      case (true, Invalid(errs)) =>
        logger.warn(
          s"""$forItem is not a valid label item, so skipping geojson import.\n${errs.toList
            .mkString("\n")}"""
        ) map { _ => List.empty }
      case _ =>
        geojsonAssets.zipWithIndex traverse {
          case ((assetKey, asset), idx) =>
            readJsonFromPath[JsonFeatureCollection](makeAbsPath(fromPath, asset.href)) map {
              featureCollection =>
                val parentCollectionHref =
                  s"/collections/${URLEncoder.encode(inCollection.id, StandardCharsets.UTF_8.toString)}"
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
                  "0.9.0",
                  Nil,
                  s"${forItem.id}-labels-${idx + 1}",
                  Some(s"${forItem.id} Labels"),
                  s"Labels for ${forItem.id}'s ${assetKey} asset",
                  Nil,
                  Proprietary(),
                  Nil,
                  inCollection.extent.copy(spatial = SpatialExtent(List(forItem.bbox))),
                  ().asJsonObject,
                  forItem.properties,
                  List(parentCollectionLink, derivedFromItemLink)
                )
                val featureItems =
                  featureCollection.getAllFeatures[Feature[Geometry, JsonObject]] map { feature =>
                    FeatureExtractor.toItem(
                      feature,
                      forItem,
                      inCollection.id,
                      labelCollection
                    )
                  }

                val newAsset = Map(
                  s"Label collection ${idx + 1}" -> StacItemAsset(
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
      stacCollection <- readJsonFromPath[StacCollection](path)
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
        .traverse { c =>
          def links(path: String): IO[List[StacLink]] =
            readJsonFromPath[StacCollection](path).map(_ => List(c)) orElse {
              readJsonFromPath[StacCatalog](path)
                .flatMap {
                  _.links
                    .filter(_.rel == StacLinkType.Child)
                    .traverse { c => links(makeAbsPath(catalogRoot, c.href)) }
                    .map(_.flatten)
                }
            }

          links(makeAbsPath(catalogRoot, c.href))
        }
        .flatMap {
          _.flatten.traverse(c => readCollectionWrapper(makeAbsPath(catalogRoot, c.href), None))
        }
      _ <- collections
        .traverse(c => {
          insertCollection(c.updateLinks) map { _ =>
            println(s"Inserted collection: ${c.value.id}")
          }
        })
        .transact(xa)
    } yield ()
  }
}
