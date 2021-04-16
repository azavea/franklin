package com.azavea.franklin.extensions.validation

import cats.effect.IO
import cats.syntax.all._
import com.azavea.franklin.api.TestImplicits
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.testing.JvmInstances._
import com.azavea.stac4s.testing._
import eu.timepit.refined.types.string.NonEmptyString
import org.specs2.{ScalaCheck, Specification}

import scala.concurrent.ExecutionContext.Implicits.global

class ValidationSpec extends Specification with ScalaCheck with TestImplicits[IO] {

  def is = s2"""
  This specification verifies that the Validation extension behaves sensibly

  The validation extension should:
    - report all the extensions it tries to validate   $validateSeveralExpectation
    - accumulate errors from checked extensions        $accumulateErrorsExpectation
"""

  implicit val contextShift = IO.contextShift(global)
  implicit val timer        = IO.timer(global)

  def validateSeveralExpectation = prop { (item: StacItem) =>
    val testIO = for {
      ref       <- itemExtensionsRef[IO]
      validator <- makeItemValidator(item.stacExtensions ++ List("label", "eo"), ref)
    } yield validator(item)
    val test = testIO.unsafeRunSync
      .getExtensionFields[ValidationExtension]
      .toEither
      .right
      .get
      .attemptedExtensions
      .toList
      .toSet
      .map { (s: NonEmptyString) => s.value }
    val expectation = Set("label", "eo")
    test should beTypedEqualTo(expectation)
  }

  def accumulateErrorsExpectation = prop { (item: StacItem) =>
    (for {
      ref              <- itemExtensionsRef[IO]
      validateLabel    <- makeItemValidator[IO](List("label"), ref)
      validateEO       <- makeItemValidator[IO](List("eo"), ref)
      combinedValidate <- makeItemValidator[IO](List("eo", "label"), ref)
    } yield {
      val labelValidated    = validateLabel(item)
      val layerValidated    = validateEO(item)
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

      test should beTypedEqualTo(expectation)
    }).unsafeRunSync

  }

}
