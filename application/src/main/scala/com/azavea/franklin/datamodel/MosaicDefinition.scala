package com.azavea.franklin.datamodel

import cats.data.NonEmptyList
import cats.kernel.Eq
import cats.syntax.apply._
import cats.syntax.contravariant._
import com.azavea.stac4s.TwoDimBbox
import io.circe.DecodingFailure
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import java.util.UUID

final case class MapCenter(longitude: Double, latitude: Double, zoom: Int)

object MapCenter {

  implicit val decMapCenter: Decoder[MapCenter] = { cursor =>
    cursor.as[List[Json]] flatMap {
      case j1 :: j2 :: j3 :: Nil =>
        (j1.as[Double], j2.as[Double], j3.as[Int]) mapN {
          MapCenter.apply
        }
      case _ =>
        Left(
          DecodingFailure(
            "Map center must be a three-element tuple of the form [longitude, latitude, zoom]",
            cursor.history
          )
        )
    }
  }

  implicit val encMapCenter: Encoder[MapCenter] = Encoder[List[Json]] contramap { center =>
    List(center.longitude.asJson, center.latitude.asJson, center.zoom.asJson)
  }

  implicit val eqMapCenter: Eq[MapCenter] = Eq.fromUniversalEquals
}

@JsonCodec final case class ItemAsset(
    itemId: String,
    assetName: String
)

final case class MosaicDefinition(
    id: UUID,
    description: Option[String],
    center: MapCenter,
    items: NonEmptyList[ItemAsset],
    minZoom: Int = 2,
    maxZoom: Int = 30,
    bounds: TwoDimBbox = TwoDimBbox(-180, -90, 180, 90)
)

object MosaicDefinition {

  private def ensureCenterInBounds(definition: MosaicDefinition): List[String] = {
    if (definition.center.zoom <= definition.maxZoom && definition.center.zoom >= definition.minZoom) {
      Nil
    } else {
      List(
        s"Default zoom of ${definition.center.zoom} is outside the range ${definition.minZoom} to ${definition.maxZoom}"
      )
    }
  }

  implicit val encMosaicDefinition: Encoder[MosaicDefinition] = deriveEncoder

//   implicit val decMosaicDefinition: Decoder[MosaicDefinition] = { cursor =>
//     for {
//         description <- cursor.get[Option[String]]("description")
//         center <- cursor.get[MapCenter]("center")
//         items <- cursor.get[NonEmptyList[ItemAsset]]("items")
//         minZoom <- cursor.get[Int]("minZoom")

//     }
  implicit val decMosaicDefinition: Decoder[MosaicDefinition] =
    deriveDecoder[MosaicDefinition].ensure(definition => ensureCenterInBounds(definition))
}
