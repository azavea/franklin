package com.azavea.franklin.database

import doobie.Fragment

import scala.annotation.implicitNotFound

/**
  * This case class is provided to allow the production of rules for transforming datatypes to doobie fragments
  */
@implicitNotFound(
  "No instance of Filterable.scala[${Model}, ${T}] in scope, check imports and make sure one is defined"
)
final case class Filterable[-Model, T](toFilters: T => List[Option[Fragment]])
