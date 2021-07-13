package com.azavea.franklin.api

import cats.data.NonEmptyList
import sttp.tapir._

package object endpoints {

  def prependApiPath(prefix: String, basePath: EndpointInput[Unit]): EndpointInput[Unit] = {
    val strippedBase = prefix.stripPrefix("/").stripSuffix("/")
    val pathComponents: NonEmptyList[String] =
      NonEmptyList.fromListUnsafe(strippedBase.split("/").toList)
    val baseInput =
      pathComponents.tail.foldLeft(pathComponents.head: EndpointInput[Unit])((x, y) => x / y)
    baseInput / basePath
  }

  def baseFor(prefix: Option[String], basePath: EndpointInput[Unit]) =
    prefix.fold(basePath)(prefix => prependApiPath(prefix, basePath))
}
