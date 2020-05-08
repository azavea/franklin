package com.azavea.franklin.crawler

import com.azavea.stac4s.StacLink
import com.azavea.stac4s.TwoDimBbox
import com.azavea.stac4s._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.methods.Implicits._
import geotrellis.vector.{Feature, Geometry}
import io.circe.JsonObject

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object FeatureExtractor {

  def toItem(
      feature: Feature[Geometry, JsonObject],
      forItem: StacItem,
      forItemCollection: String,
      inCollection: StacCollection,
      serverHost: NonEmptyString
  ): StacItem = {
    val collectionHref =
      s"$serverHost/collections/${URLEncoder.encode(inCollection.id, StandardCharsets.UTF_8.toString)}"
    val encodedSourceItemCollectionId =
      URLEncoder.encode(forItemCollection, StandardCharsets.UTF_8.toString)
    val sourceItemHref =
      s"$serverHost/collections/$encodedSourceItemCollectionId/items/${URLEncoder.encode(forItem.id, StandardCharsets.UTF_8.toString)}"

    val collectionLink = StacLink(
      collectionHref,
      StacLinkType.Collection,
      Some(`application/json`),
      title = Some("Source item's original collection"),
      Nil
    )

    val sourceItemLink = StacLink(
      sourceItemHref,
      StacLinkType.VendorLinkType("derived_from"),
      Some(`application/json`),
      None,
      Nil
    )

    val featureExtent = feature.geom.extent

    StacItem(
      s"${UUID.randomUUID}",
      "0.9.0",
      Nil,
      "Feature",
      feature.geom,
      TwoDimBbox(featureExtent.xmin, featureExtent.ymin, featureExtent.xmax, featureExtent.ymax),
      links = List(collectionLink, sourceItemLink),
      assets = Map.empty,
      collection = Some(inCollection.id),
      properties = feature.data
    )
  }

}
