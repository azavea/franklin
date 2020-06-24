package com.azavea.franklin.crawler

import com.azavea.stac4s._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
      rootLink: StacLink
  ) = {
    val encodedItemId       = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)
    val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
    val selfLink = StacLink(
      s"/collections/$encodedCollectionId/items/$encodedItemId",
      StacLinkType.Self,
      Some(`application/geo+json`),
      Some(item.id)
    )

    val parentLink = StacLink(
      s"/collections/$encodedCollectionId/",
      StacLinkType.Parent,
      Some(`application/json`),
      collection.title
    )

    val collectionLink = parentLink.copy(rel = StacLinkType.Collection)

    val updatedLinks =
      filterLinks(item.links) ++ List(selfLink, parentLink, collectionLink, rootLink)
    item.copy(links = updatedLinks)

  }

  // Updates links for collection and its children + items
  def updateLinks: CollectionWrapper = {

    val rootLink = StacLink(
      "/",
      StacLinkType.StacRoot,
      None,
      None
    )

    val collectionId = URLEncoder.encode(value.id, StandardCharsets.UTF_8.toString)

    val itemLinks = items.map { item =>
      val itemId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)

      StacLink(
        s"/collections/$collectionId/items/$itemId",
        StacLinkType.Item,
        Some(`application/geo+json`),
        Some(item.id)
      )
    }

    val childrenLinks = children.map { child =>
      val encodedChildId = URLEncoder.encode(child.value.id, StandardCharsets.UTF_8.toString)
      StacLink(
        s"/collections/$encodedChildId/",
        StacLinkType.Child,
        Some(`application/json`),
        child.value.title
      )
    }

    val parentLink = parent.map { p =>
      val encodedParentId = URLEncoder.encode(p.value.id, StandardCharsets.UTF_8.toString)
      List(
        StacLink(
          s"/collections/$encodedParentId/",
          StacLinkType.Parent,
          Some(`application/json`),
          p.value.title
        )
      )
    }

    val selfLink = StacLink(
      s"/collections/$collectionId/",
      StacLinkType.Self,
      Some(`application/json`),
      value.title
    )
    val updatedLinks =
      selfLink :: rootLink :: filterLinks(value.links) ++ childrenLinks ++ itemLinks ++ parentLink
        .getOrElse(
          List.empty
        )

    CollectionWrapper(
      value.copy(links = updatedLinks),
      parent,
      children.map(_.updateLinks),
      items.map(i => updateItemLinks(value, i, rootLink))
    )
  }
}
