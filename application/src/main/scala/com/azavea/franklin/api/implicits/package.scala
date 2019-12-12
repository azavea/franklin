package com.azavea.franklin.api

import eu.timepit.refined.types.string.NonEmptyString

package object implicits {

  implicit class combineNonEmptyString(s: NonEmptyString) {

    // Combining a non-empty string should always return a non-empty string
    def +(otherString: String): NonEmptyString =
      NonEmptyString.unsafeFrom(s.value.concat(otherString))
  }

}
