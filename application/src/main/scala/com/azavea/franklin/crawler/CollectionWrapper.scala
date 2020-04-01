package com.azavea.franklin.crawler

import com.azavea.stac4s._
import eu.timepit.refined.types.string.NonEmptyString

// A wrapper that stores a collection, an optional parent, its children, and its items
case class CollectionWrapper(
    value: StacCollection,
    parent: Option[CollectionWrapper],
    children: List[CollectionWrapper],
    items: List[StacItem]
) {

  private def updateItemLinks(
      collection: StacCollection,
      item: StacItem,
      serverHost: NonEmptyString,
      rootLink: StacLink
  ) = {

    val selfLink = StacLink(
      s"${serverHost.value}/collections/${collection.id}/items/${item.id}",
      StacLinkType.Self,
      Some(`application/geo+json`),
      Some(item.id),
      List.empty
    )

    val parentLink = StacLink(
      s"${serverHost.value}/collections/${collection.id}/",
      StacLinkType.Parent,
      Some(`application/json`),
      collection.title,
      List.empty
    )

    val collectionLink = parentLink.copy(rel = StacLinkType.Collection)

    val updatedLinks =
      filterLinks(item.links) ++ List(selfLink, parentLink, collectionLink, rootLink)
    item.copy(links = updatedLinks)

  }

  // Updates links for collection and its children + items
  def updateLinks(serverHost: NonEmptyString): CollectionWrapper = {

    val rootLink = StacLink(
      s"${serverHost.value}/",
      StacLinkType.StacRoot,
      None,
      None,
      List.empty
    )

    val collectionId = value.id

    val itemLinks = items.map { item =>
      StacLink(
        s"${serverHost.value}/collections/$collectionId/items/${item.id}",
        StacLinkType.Item,
        Some(`application/geo+json`),
        Some(item.id),
        List.empty
      )
    }

    val childrenLinks = children.map { child =>
      StacLink(
        s"${serverHost.value}/collections/${child.value.id}/",
        StacLinkType.Child,
        Some(`application/json`),
        child.value.title,
        List.empty
      )
    }

    val parentLink = parent.map { p =>
      List(
        StacLink(
          s"${serverHost.value}/collections/${p.value.id}/",
          StacLinkType.Parent,
          Some(`application/json`),
          p.value.title,
          List.empty
        )
      )
    }

    val selfLink = StacLink(
      s"${serverHost.value}/collections/$collectionId/",
      StacLinkType.Self,
      Some(`application/json`),
      value.title,
      List.empty
    )
    val updatedLinks =
      selfLink :: rootLink :: filterLinks(value.links) ++ childrenLinks ++ itemLinks ++ parentLink
        .getOrElse(
          List.empty
        )

    CollectionWrapper(
      value.copy(links = updatedLinks),
      parent,
      children.map(_.updateLinks(serverHost)),
      items.map(i => updateItemLinks(value, i, serverHost, rootLink))
    )
  }
}
