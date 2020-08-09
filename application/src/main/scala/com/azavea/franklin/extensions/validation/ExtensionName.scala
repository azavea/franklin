package com.azavea.franklin.extensions.validation

import io.circe.{Decoder, Encoder}

sealed abstract class ExtensionName(val repr: String) {
  override def toString: String = repr
}

object ExtensionName {

  def fromString(s: String): ExtensionName = s.toLowerCase match {
    case "label" => Label
    case "layer" => Layer
    case "eo"    => EO
    case _       => Unchecked(s)
  }

  implicit val encExtensionName: Encoder[ExtensionName] = Encoder.encodeString.contramap(_.repr)
  implicit val decExtensionName: Decoder[ExtensionName] = Decoder.decodeString.map(fromString)
}

case object Label                        extends ExtensionName("label")
case object Layer                        extends ExtensionName("layer")
case object EO                           extends ExtensionName("eo")
case class Unchecked(underlying: String) extends ExtensionName(underlying)
