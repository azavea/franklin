package com.azavea.franklin.datamodel.hierarchy

import com.azavea.franklin.datamodel._

import cats.syntax.option._
import com.azavea.stac4s.`application/json`
import com.azavea.stac4s.StacLinkType


sealed trait StacHierarchy { self =>
  val children: List[StacHierarchy]
  val items: List[ItemPath]
  def childLink(apiHost: String): Link
  def _id: String
  val path: List[String]


  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy

  // Recursively update paths on tree
  def updatePaths(previousPaths: List[String] = List.empty[String]): StacHierarchy = {
    val newPath = previousPaths :+ _id
    updatePath(
      newPath,
      children.map(_.updatePaths(newPath))
    )
  }


  def findCatalog(relativePath: List[String]): Option[StacHierarchy] =
    if (relativePath.length == 0) {
      this.some
    } else if (relativePath.length == 1) {
      children.find(child => child.path == path :+ relativePath.head)
    } else {
      children.find(child => child.path == path :+ relativePath.head).flatMap { found =>
        found.findCatalog(relativePath.tail)
      }
    }

  def itemLinks(apiHost: String): List[Link] = items.map { itemPath =>
    Link(
      href=s"${apiHost}/collections/${itemPath.collectionId}/items/${itemPath.itemId}",
      rel=StacLinkType.Item,
      _type=`application/json`.some
    )
  }
}

object StacHierarchy {
  def empty: StacHierarchy = RootNode(List(), List()).asInstanceOf[StacHierarchy]
}


final case class RootNode(children: List[StacHierarchy], items: List[ItemPath]) extends StacHierarchy {
  def childLink(apiHost: String): Link =
    throw new Exception("No child link should be constructed for RootNode instances")
  def _id: String = "__root__"
  val path: List[String] = List.empty[String]

  override def updatePaths(previousPaths: List[String] = List.empty[String]): StacHierarchy = {
    val newPath = List.empty[String]
    updatePath(
      newPath,
      children.map(_.updatePaths(newPath))
    )
  }

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy = {
    assert(newPath.isEmpty)
    this.copy(children=newChildren)
  }
}

object RootNode {
  def apply(children: List[StacHierarchy], items: List[ItemPath]): RootNode =
    new RootNode(children, items).updatePaths().asInstanceOf[RootNode]
}


final case class CollectionNode(
  collectionId: String,
) extends StacHierarchy {
  val children: List[StacHierarchy] = List.empty[StacHierarchy]
  val path: List[String] = List.empty[String]
  val items: List[ItemPath] = List.empty[ItemPath]

  val _id = collectionId

  def childLink(apiHost: String): Link = Link(
    href=s"${apiHost}/collections/${collectionId}",
    rel=StacLinkType.Child,
    _type=`application/json`.some
    // Ideally, we will look up the collection's title from the DB and hold it in memory (perhaps at startup?)
    //title=title.orElse(collectionId.some)
  )

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy =
    this
}

final case class CatalogNode(
  catalogId: String,
  title: Option[String],
  description: String,
  children: List[StacHierarchy],
  items: List[ItemPath],
  path: List[String] = List.empty[String]
) extends StacHierarchy {

  val _id = catalogId

  def childLink(apiHost: String): Link = Link(
    href=s"${apiHost}/catalogs/${path.mkString("/")}",
    rel=StacLinkType.Child,
    _type=`application/json`.some,
    title=title
  )

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy =
    this.copy(path=newPath, children=newChildren)


  def createCatalog = {
    Catalog(
      "1.0.0",
      List(),
      catalogId,
      title,
      description,
      List(),
      None
    )
  }
}