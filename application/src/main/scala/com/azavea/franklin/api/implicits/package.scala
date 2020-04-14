package com.azavea.franklin.api

import com.azavea.stac4s._
import eu.timepit.refined.types.string.NonEmptyString

package object implicits {

  implicit class combineNonEmptyString(s: NonEmptyString) {

    // Combining a non-empty string should always return a non-empty string
    def +(otherString: String): NonEmptyString =
      NonEmptyString.unsafeFrom(s.value.concat(otherString))
  }

  implicit class StacItemWithCog(item: StacItem) {

    def addTilesLink(apiHost: String, collectionId: String, itemId: String) = {
      val cogAsset = item.assets.values.exists { asset =>
        asset._type match {
          case Some(`image/cog`) => true
          case _                 => false
        }
      }
      val updatedLinks = cogAsset match {
        case true => {
          val tileLink: StacLink = StacLink(
            s"$apiHost/collections/$collectionId/items/$itemId/tiles",
            StacLinkType.VendorLinkType("tiles"),
            Some(`application/json`),
            Some("Tile URLs for Item"),
            List.empty
          )
          tileLink :: item.links
        }
        case _ => item.links
      }
      (item.copy(links = updatedLinks))
    }
  }

}
