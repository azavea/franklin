package com.azavea.franklin.datamodel.stactypes

import com.azavea.franklin.datamodel.Link
import com.azavea.stac4s._

import com.azavea.stac4s.{StacCollection}
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._


case class Collection(
  id: String,
  description: String,
  links: List[Link],
  stacExtensions: Option[List[String]],
  title: Option[String],
  _type: String,
  assets: Map[String, Asset],
  license: String,
  extent: Extent,
  keywords: Option[List[String]],
  providers: Option[List[Provider]],
  summaries: Option[Json],
  extraFields: JsonObject = ().asJsonObject
) {
  val stacVersion: String = "1.0.0"
}
object Collection {

  val collectionFields = productFieldNames[Collection]

  implicit val encCollection: Encoder[Collection] = new Encoder[Collection] {
    def apply(collection: Collection): Json = {
      val baseEncoder: Encoder[Collection] = Encoder.forProduct13(
        "id",
        "description",
        "links",
        "stac_extensions",
        "stac_version",
        "title",
        "type",
        "assets",
        "license",
        "extent",
        "keywords",
        "providers",
        "summaries"
      )( coll =>
        (
          coll.id,
          coll.description,
          coll.links,
          coll.stacExtensions,
          coll.stacVersion,
          coll.title,
          coll._type,
          coll.assets,
          coll.license,
          coll.extent,
          coll.keywords,
          coll.providers,
          coll.summaries
        )
      )

      baseEncoder(collection).deepMerge(collection.extraFields.asJson).dropNullValues
    }
  }

  implicit val decodeCollection: Decoder[Collection] = new Decoder[Collection] {
    final def apply(c: HCursor): Decoder.Result[Collection] =
      for {
        id <- c.downField("id").as[String]
        description <- c.downField("description").as[String]
        links <- c.downField("links").as[List[Link]]
        stacExtensions <- c.downField("stac_extensions").as[Option[List[String]]]
        title <- c.downField("title").as[Option[String]]
        _type <- c.downField("type").as[String]
        assets <- c.downField("assets").as[Map[String, Asset]]
        license <- c.downField("license").as[String]
        extent <- c.downField("extent").as[Extent]
        keywords <- c.downField("keywords").as[Option[List[String]]]
        providers <- c.downField("providers").as[Option[List[Provider]]]
        summaries <- c.downField("summaries").as[Option[Json]]
        document <- c.value.as[JsonObject]
      } yield {
        Collection(
          id,
          description,
          links,
          stacExtensions,
          title,
          _type,
          assets,
          license,
          extent,
          keywords,
          providers,
          summaries,
          document.filter({ case (k, _) =>
            !collectionFields.contains(k)
          })
        )
      }
  }
}