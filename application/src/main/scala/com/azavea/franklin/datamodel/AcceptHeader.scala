package com.azavea.franklin.datamodel

case class AcceptHeader(v: String) {
    private val mediaTypeStrings = v.split(",")
    lazy val acceptJson = mediaTypeStrings.contains("application/json")
}
