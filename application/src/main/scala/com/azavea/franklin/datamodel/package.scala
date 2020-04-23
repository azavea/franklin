package com.azavea.franklin

import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._
import io.circe.refined._
import io.circe.{Decoder, Encoder}

package object datamodel {
  implicit val decoderNonEmptyString: Decoder[NonEmptyString] = refinedDecoder
  implicit val encoderNonEmptyString: Encoder[NonEmptyString] = refinedEncoder

  type Quantile = Int Refined Interval.Closed[W.`0`.T, W.`100`.T]
  object Quantile extends RefinedTypeOps[Quantile, Int]

}
