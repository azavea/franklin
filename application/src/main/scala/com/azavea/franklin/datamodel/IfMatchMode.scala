package com.azavea.franklin.datamodel

import cats.kernel.Eq

sealed abstract class IfMatchMode(repr: String) {
  override def toString = repr
  def matches(s: String): Boolean
}

object IfMatchMode {

  case object YOLO extends IfMatchMode("*") {
    def matches(tag: String) = true
  }

  case class Safe(s: String) extends IfMatchMode(s) {
    def matches(tag: String) = s == tag
  }

  implicit val eqIfMatchMode: Eq[IfMatchMode] = Eq.fromUniversalEquals

  def fromString: String => IfMatchMode = {
    case "*" => YOLO
    case s   => Safe(s)
  }
}
