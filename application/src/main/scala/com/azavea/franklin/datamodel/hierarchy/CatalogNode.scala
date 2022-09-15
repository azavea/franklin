package com.azavea.franklin.datamodel.hierarchy

import cats.syntax.option._
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s.`application/json`

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
    href = s"${apiHost}/catalogs/${path.mkString("/")}",
    rel = StacLinkType.Child,
    _type = `application/json`.some,
    title = title
  )

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy =
    this.copy(path = newPath, children = newChildren)

  def createCatalog(apiHost: String) = {
    val links = List(
      Link(
        apiHost,
        StacLinkType.Self,
        Some(`application/json`)
      )
    ) ++ this.childLinks(apiHost) ++ this.itemLinks(apiHost)
    Catalog(
      "1.0.0",
      List(),
      catalogId,
      title,
      description,
      links,
      None
    )
  }
}
