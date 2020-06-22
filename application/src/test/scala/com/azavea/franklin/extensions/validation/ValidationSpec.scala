package com.azavea.franklin.extensions.validation

import cats.implicits._
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.testing._
import org.specs2.{ScalaCheck, Specification}
import eu.timepit.refined.types.string.NonEmptyString

class ValidationSpec extends Specification with ScalaCheck {

  def is = s2"""
  This specification verifies that the Validation extension behaves sensibly

  The validation extension should:
    - report all the extensions it tries to validate   $validateSeveralExpectation
    - accumulate errors from checked extensions        $accumulateErrorsExpectation
"""

  def validateSeveralExpectation = prop { (item: StacItem) =>
    val validate = getItemValidator(List(Label, Layer))
    val test = validate(item)
      .getExtensionFields[ValidationExtension]
      .toEither
      .right
      .get
      .attemptedExtensions
      .toList
      .toSet
      .map { (s: NonEmptyString) => s.value }
    val expectation = Set(Label, Layer) map { _.repr }
    test == expectation
  }

  def accumulateErrorsExpectation = prop { (item: StacItem) =>
    val validateLabel    = getItemValidator(List(Label))
    val validateLayer    = getItemValidator(List(Layer))
    val combinedValidate = getItemValidator(List(Label, Layer))

    val labelValidated    = validateLabel(item)
    val layerValidated    = validateLayer(item)
    val combinedValidated = combinedValidate(item)

    val test = (labelValidated.getExtensionFields[ValidationExtension] |+| layerValidated
      .getExtensionFields[ValidationExtension]).toEither.right.get.errors.toSet

    val expectation = combinedValidated
      .getExtensionFields[ValidationExtension]
      .toEither
      .right
      .get
      .errors
      .toSet

    test == expectation
  }

}
