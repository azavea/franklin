package com.azavea.franklin.crawler

import com.azavea.stac4s._
import io.circe.JsonObject
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Feature, Geometry}
import geotrellis.vector.methods.Implicits._

import java.net.URLEncoder
import com.azavea.stac4s.StacLink

import java.util.UUID
import com.azavea.stac4s.TwoDimBbox
import java.nio.charset.StandardCharsets

object FeatureExtractor {

  def toItem(
      feature: Feature[Geometry, JsonObject],
      forItem: StacItem,
      inCollection: StacCollection,
      serverHost: NonEmptyString
  ): StacItem = {
    val newItemId = s"${UUID.randomUUID}"

    val collectionHref =
      s"$serverHost/collections/${URLEncoder.encode(inCollection.id, StandardCharsets.UTF_8.toString)}"
    val sourceItemHref =
      s"$collectionHref/items/${URLEncoder.encode(forItem.id, StandardCharsets.UTF_8.toString)}"
    val selfHref = s"$collectionHref/items/$newItemId"

    val collectionLink = StacLink(
      collectionHref,
      StacLinkType.Parent,
      Some(`application/json`),
      title = Some("Source item's original collection"),
      Nil
    )

    val sourceItemLink = StacLink(
      sourceItemHref,
      StacLinkType.Source,
      Some(`application/json`),
      title = Some("Source item"),
      Nil
    )

    val selfLink = StacLink(
      selfHref,
      StacLinkType.Self,
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
      links = List(collectionLink, sourceItemLink, selfLink),
      assets = Map.empty,
      collection = Some(inCollection.id),
      properties = feature.data
    )
  }

}
