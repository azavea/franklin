package com.azavea.franklin.datamodel

sealed abstract class SearchMethod

object SearchMethod {
  case object Post extends SearchMethod
  case object Get  extends SearchMethod
}
