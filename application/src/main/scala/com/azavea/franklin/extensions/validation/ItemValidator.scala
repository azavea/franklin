package com.azavea.franklin.extensions.validation

import com.azavea.stac4s.StacItem
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.extensions.ItemExtension
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.auto._
import com.azavea.stac4s.extensions.label.LabelItemExtension
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.extensions.eo.EOItemExtension

trait ItemValidator[T] {
  def validate(item: StacItem): StacItem
}

object ItemValidator {

  def apply[T](implicit ev: ItemValidator[T]) = ev

  def instance[T: ItemExtension](name: NonEmptyString) = new ItemValidator[T] {

    def validate(item: StacItem) = {
      val extensionResult     = item.getExtensionFields[T]
      val validationExtension = ValidationExtension.fromResult(extensionResult, name)
      item.addExtensionFields(validationExtension)
    }
  }

  implicit val labelItemValidator: ItemValidator[LabelItemExtension] = instance("label")
  implicit val layerItemValidator: ItemValidator[LayerItemExtension] = instance("layer")
  implicit val eoItemValidator: ItemValidator[EOItemExtension]       = instance("eo")

}
