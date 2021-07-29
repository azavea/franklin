package com.azavea.franklin.api

import cats.data.Validated
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.datamodel.MosaicDefinition
import com.azavea.stac4s._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.syntax._
import io.circe.{CursorOp, Decoder, DecodingFailure, Encoder, ParsingFailure}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.SchemaType._
import sttp.tapir.{DecodeResult, FieldName, Schema}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

package object implicits {

  implicit class combineNonEmptyString(s: NonEmptyString) {

    // Combining a non-empty string should always return a non-empty string
    def +(otherString: String): NonEmptyString =
      NonEmptyString.unsafeFrom(s.value.concat(otherString))
  }

  implicit class StacItemWithCog(item: StacItem) {

    def updateLinksWithHost(apiConfig: ApiConfig) = {
      val updatedLinks = item.links.map(_.addServerHost(apiConfig))
      val updatedAssets = item.assets.mapValues { asset =>
        asset.href.startsWith("/") match {
          case true => asset.copy(href = s"${apiConfig.apiHost}${asset.href}")
          case _    => asset
        }
      }
      item.copy(links = updatedLinks, assets = updatedAssets)
    }

    def addTilesLink(apiHost: String, collectionId: String, itemId: String) = {
      val cogAsset = item.assets.values.exists { asset =>
        asset._type match {
          case Some(`image/cog`) => true
          case _                 => false
        }
      }
      val updatedLinks = cogAsset match {
        case true => {
          val encodedItemId       = URLEncoder.encode(itemId, StandardCharsets.UTF_8.toString)
          val encodedCollectionId = URLEncoder.encode(collectionId, StandardCharsets.UTF_8.toString)
          val tileLink: StacLink = StacLink(
            s"$apiHost/collections/$encodedCollectionId/items/$encodedItemId/tiles",
            StacLinkType.VendorLinkType("tiles"),
            Some(`application/json`),
            Some("Tile URLs for Item")
          )
          tileLink :: item.links
        }
        case _ => item.links
      }
      (item.copy(links = updatedLinks))
    }

    def addRootLink(link: StacLink): StacItem =
      item.copy(
        links = item.links.filter(_.rel != StacLinkType.StacRoot) :+ link
      )
  }

  implicit class UpdatedStacLink(link: StacLink) {

    def addServerHost(apiConfig: ApiConfig) = {
      link.href.startsWith("/") match {
        case true => link.copy(href = s"${apiConfig.apiHost}${link.href}")
        case _    => link
      }
    }
  }

  implicit class StacCollectionWithTiles(collection: StacCollection) {

    def selfLink(apiHost: String): String = {
      val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
      s"$apiHost/collections/$encodedCollectionId/",
    }

    def addTilesLink(apiHost: String): StacCollection = {
      val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
      val tileLink = StacLink(
        s"$apiHost/collections/$encodedCollectionId/tiles",
        StacLinkType.VendorLinkType("tiles"),
        Some(`application/json`),
        Some("Tile URLs for Collection")
      )
      collection.copy(links = tileLink :: collection.links)
    }

    def addMosaicLinks(apiHost: String, mosaicDefinitions: List[MosaicDefinition]) = {
      val encodedCollectionId = URLEncoder.encode(collection.id, StandardCharsets.UTF_8.toString)
      val mosaicLinks = mosaicDefinitions map { mosaicDefinition =>
        StacLink(
          s"$apiHost/collections/$encodedCollectionId/mosaic/${mosaicDefinition.id}",
          StacLinkType.VendorLinkType("mosaic-definition"),
          Some(`application/json`),
          mosaicDefinition.description orElse Some(s"Mosaic ${mosaicDefinition.id}")
        )
      }
      collection.copy(links = collection.links ++ mosaicLinks)
    }

    def maybeAddTilesLink(enableTiles: Boolean, apiHost: String) =
      if (enableTiles) addTilesLink(apiHost) else collection

    def maybeAddMosaicLinks(
        enableTiles: Boolean,
        apiHost: String,
        mosaicDefinitions: List[MosaicDefinition]
    ) =
      if (enableTiles) addMosaicLinks(apiHost, mosaicDefinitions) else collection

    def updateLinksWithHost(apiConfig: ApiConfig) = {
      val updatedLinks = collection.links.map(_.addServerHost(apiConfig))
      collection.copy(links = updatedLinks)
    }
  }

  private def circeErrorToFailure(
      failure: io.circe.Error
  ): DecodeResult.Error.JsonError = failure match {
    case DecodingFailure(err, hist) =>
      val path   = CursorOp.opsToPath(hist)
      val fields = path.split("\\.").toList.filter(_.nonEmpty).map(FieldName.apply)
      DecodeResult.Error.JsonError(err, fields)
    case ParsingFailure(message, _) =>
      DecodeResult.Error.JsonError(message, path = List.empty)
  }

  // TODO -- borrow logic from logCirceError to print much better information about what went wrong
  implicit def circeCodec[T: Encoder: Decoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.circe.parser.decodeAccumulating[T](s) match {
        case Validated.Invalid(errs) =>
          DecodeResult.Error(s, DecodeResult.Error.JsonDecodeException(errs.toList map {
            circeErrorToFailure
          }, errs.head))
        case Validated.Valid(v) => DecodeResult.Value(v)
      }
    } { t => t.asJson.noSpaces }
}
