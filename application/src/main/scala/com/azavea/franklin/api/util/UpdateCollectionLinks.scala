package com.azavea.franklin.api.util

import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.datamodel.{Collection, Link}
import com.azavea.stac4s._

case class UpdateCollectionLinks(apiConfig: ApiConfig) {

  def createSelfLink(collection: Collection): Link = {
    Link(
      s"${apiConfig.apiHost}/collections/${collection.id}",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )
  }

  def apply(collection: Collection): Collection = {
    val selfLink = createSelfLink(collection)
    collection.copy(links = collection.links :+ selfLink)
  }
}
