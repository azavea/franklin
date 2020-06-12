package com.azavea.franklin.extensions.paging

import com.azavea.franklin.database.SearchFilters
import com.azavea.stac4s.extensions.LinkExtension
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

final case class PagingLinkExtension(
    headers: Map[NonEmptyString, String],
    body: SearchFilters,
    merge: Boolean
)

object PagingLinkExtension {
  implicit val decPagingLinkExtension: Decoder[PagingLinkExtension] = deriveDecoder

  implicit val encPagingLinkExtension: Encoder.AsObject[PagingLinkExtension] = Encoder
    .AsObject[Map[String, Json]]
    .contramapObject((linkExtensionFields: PagingLinkExtension) =>
      Map(
        "method"  -> "POST".asJson,
        "headers" -> linkExtensionFields.headers.asJson,
        "body"    -> linkExtensionFields.body.asJson,
        "merge"   -> linkExtensionFields.merge.asJson
      )
    )

  implicit val linkExtensionPagingLinkExtension: LinkExtension[PagingLinkExtension] =
    LinkExtension.instance
}
