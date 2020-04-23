package com.azavea.franklin.api

import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._

package object endpoints {

  type Quantile = Int Refined Interval.Closed[W.`0`.T, W.`100`.T]
  object Quantile extends RefinedTypeOps[Quantile, Int]

}
