package com.azavea.franklin.datamodel

import com.azavea.franklin.datamodel.hierarchy._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

class StacHierarchySpec extends AnyFlatSpec {

    val hierarchy = RootNode(
      List(
        CollectionNode("joplin"),
        CatalogNode("joplin-test", None, "A test of catalogs",
          List(
            CatalogNode("sub-joplin", None, "A more serious test",
              List(
                CollectionNode("joplin")
              ),
              List()
            ),
            CollectionNode("joplin")
          ),
          List(ItemPath("joplin", "fe916452-ba6f-4631-9154-c249924a122d"))
      )),
      List(ItemPath("naip", "al_m_3008506_nw_16_060_20191118_20200114"), ItemPath("joplin", "fe916452-ba6f-4631-9154-c249924a122d"))
    )
  
  "A hierarchy" should "roundtrip serialize and deserialize" in {
    hierarchy.asJson.as[StacHierarchy] shouldBe ('right)
  }

  "A hierarchy" should "automatically register paths to nodes in apply" in {
    hierarchy.children
      .find({ child =>
        child.path.length == 1 && child.path.head == "joplin-test"
      }) shouldBe ('defined)
  }

  "A hierarchy" should "automatically register paths to nodes upon deserialization" in {
    hierarchy.asJson.as[StacHierarchy].right.get
      .children.find({ child =>
        child.path.length == 1 && child.path.head == "joplin-test"
      }) shouldBe ('defined)
  }
}
