package com.azavea.franklin.database

import io.circe.generic.semiauto._

import scala.util.Try

final case class Page(limit: Option[Int], next: Option[String]) {
  lazy val pageInt: Option[Int] = next.flatMap(v => Try(v.toInt).toOption)

  lazy val offset: Int = pageInt match {
    case Some(offsetInt) => offsetInt * limit.getOrElse(20)
    case _               => 0
  }

  lazy val nextPage = Page(limit, pageInt.map(v => s"${v + 1}"))
}

object Page {
  implicit val pageEncoder = deriveEncoder[Page]
  implicit val pageDecoder = deriveDecoder[Page]
}
