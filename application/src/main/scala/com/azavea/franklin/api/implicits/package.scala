package com.azavea.franklin.api

import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.stac4s._
import eu.timepit.refined.types.string.NonEmptyString

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

package object implicits {

  implicit class combineNonEmptyString(s: NonEmptyString) {

    // Combining a non-empty string should always return a non-empty string
    def +(otherString: String): NonEmptyString =
      NonEmptyString.unsafeFrom(s.value.concat(otherString))
  }

  implicit class StacItemWithCog(item: StacItem) {

    def updateLinksWithHost(apiConfig: ApiConfig) = {
      val updatedLinks = item.links.map(_.addServerHost(apiConfig))
      item.copy(links = updatedLinks)
    }

    def addTilesLink(apiHost: String, collectionId: String, itemId: String) = {
      val cogAsset = item.assets.values.exists { asset =>
        asset._type match {
          case Some(`image/cog`) => true
          case _                 => false
        }
      }
      val updatedLinks = cogAsset match {
        case true => {
          val encodedItemId       = URLEncoder.encode(itemId, StandardCharsets.UTF_8.toString)
          val encodedCollectionId = URLEncoder.encode(collectionId, StandardCharsets.UTF_8.toString)
          val tileLink: StacLink = StacLink(
            s"$apiHost/collections/$encodedCollectionId/items/$encodedItemId/tiles",
            StacLinkType.VendorLinkType("tiles"),
            Some(`application/json`),
            Some("Tile URLs for Item")
          )
          tileLink :: item.links
        }
        case _ => item.links
      }
      (item.copy(links = updatedLinks))
    }

  }

  implicit class UpdatedStacLink(link: StacLink) {

    def addServerHost(apiConfig: ApiConfig) = {
      link.href.startsWith("/") match {
        case true => link.copy(href = s"${apiConfig.apiHost}${link.href}")
        case _    => link
      }
    }
  }

  implicit class StacCollectionWithTiles(collection: StacCollection) {

    def addTilesLink(apiHost: String): StacCollection = {
      val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
      val tileLink = StacLink(
        s"$apiHost/collections/$encodedCollectionId/tiles",
        StacLinkType.VendorLinkType("tiles"),
        Some(`application/json`),
        Some("Tile URLs for Collection")
      )
      collection.copy(links = tileLink :: collection.links)
    }

    def maybeAddTilesLink(enableTiles: Boolean, apiHost: String) =
      if (enableTiles) addTilesLink(apiHost) else collection

    def updateLinksWithHost(apiConfig: ApiConfig) = {
      val updatedLinks = collection.links.map(_.addServerHost(apiConfig))
      collection.copy(links = updatedLinks)
    }
  }
}
