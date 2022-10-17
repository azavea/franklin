package com.azavea.franklin.datamodel.hierarchy

import cats.syntax.option._
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s.`application/json`

import scala.util.Try

trait StacHierarchy { self =>
  val children: List[StacHierarchy]
  val items: List[ItemPath]
  def _id: String
  val path: List[String]

  def childLink(apiHost: String): Link
  def childLinks(apiHost: String): List[Link] = children.map(_.childLink(apiHost))

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy

  // Recursively update paths on tree
  def updatePaths(previousPaths: List[String] = List.empty[String]): StacHierarchy = {
    val newPath = previousPaths :+ _id
    updatePath(
      newPath,
      children.map(_.updatePaths(newPath))
    )
  }

  def findCatalog(relativePath: List[String]): Option[CatalogNode] =
    if (relativePath.length == 0) {
      if (this.isInstanceOf[CatalogNode]) this.asInstanceOf[CatalogNode].some
      else None
    } else if (relativePath.length == 1) {
      children
        .find(child => child.path == path :+ relativePath.head)
        .flatMap({ found =>
          if (found.isInstanceOf[CatalogNode]) found.asInstanceOf[CatalogNode].some
          else None
        })
    } else {
      children
        .find(child => child.path == path :+ relativePath.head)
        .flatMap(found =>
          Try(relativePath.tail).toOption match {
            case Some(tail) => found.findCatalog(tail)
            case None       => None
          }
        )
    }

  def itemLinks(apiHost: String): List[Link] = items.map { itemPath =>
    Link(
      href = s"${apiHost}/collections/${itemPath.collectionId}/items/${itemPath.itemId}",
      rel = StacLinkType.Item,
      _type = `application/json`.some
    )
  }
}

object StacHierarchy {
  def empty: StacHierarchy = RootNode(List(), List()).asInstanceOf[StacHierarchy]
}
