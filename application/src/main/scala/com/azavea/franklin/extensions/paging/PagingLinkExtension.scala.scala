package com.azavea.franklin.extensions.paging

import com.azavea.franklin.datamodel.SearchParameters
import com.azavea.stac4s.extensions.LinkExtension

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

// https://github.com/radiantearth/stac-api-spec/blob/7a6e12868113c94b17dead8989f49d978d5dd865/api-spec.md#paging-extension
// the method for us is always POST, since otherwise we toss the params in the query string
// and don't use this
final case class PagingLinkExtension(
    headers: Map[NonEmptyString, String],
    body: SearchParameters,
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
