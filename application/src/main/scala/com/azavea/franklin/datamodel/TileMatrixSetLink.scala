package com.azavea.franklin.datamodel

import io.circe.generic.JsonCodec

@JsonCodec
case class TileMatrixSetLink(tileMatrixSet: String, tileMatrixSetURI: String)
