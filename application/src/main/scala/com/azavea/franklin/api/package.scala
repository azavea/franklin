package com.azavea.franklin

import com.azavea.franklin.extensions.validation.syntax._

import cats.effect.concurrent.Ref
import cats.effect.Sync
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.extensions.label.LabelItemExtension
import eu.timepit.refined.auto._
import com.azavea.stac4s.extensions.eo.EOItemExtension
import com.azavea.stac4s.StacCollection

package object api {

  private def getExtensionsRef[F[_]: Sync, T]: F[Ref[F, Map[String, T => T]]] =
    Ref.of[F, Map[String, T => T]](Map.empty)

  private def getExtensionsRef[F[_]: Sync, T](
      m: Map[String, T => T]
  ): F[Ref[F, Map[String, T => T]]] =
    Ref.of(m)

  private val knownItemExtensions: Map[String, StacItem => StacItem] = Map(
    "label" -> ((item: StacItem) => item.validate[LabelItemExtension]("label")),
    "https://raw.githubusercontent.com/stac-extensions/label/v1.0.0/json-schema/schema.json" -> (
      (item: StacItem) => item.validate[LabelItemExtension]("label")
    ),
    "eo" -> ((item: StacItem) => item.validate[EOItemExtension]("eo")),
    "https://raw.githubusercontent.com/stac-extensions/eo/v1.0.0/json-schema/schema.json" -> (
      (item: StacItem) => item.validate[EOItemExtension]("eo")
    )
  )

  def itemExtensionsRef[F[_]: Sync]       = getExtensionsRef[F, StacItem](knownItemExtensions)
  def collectionExtensionsRef[F[_]: Sync] = getExtensionsRef[F, StacCollection]
}
