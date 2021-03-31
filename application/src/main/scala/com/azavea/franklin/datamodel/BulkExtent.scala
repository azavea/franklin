package com.azavea.franklin.datamodel

import com.azavea.stac4s.TwoDimBbox

import geotrellis.vector.Extent

import java.time.Instant

final case class BulkExtent(
    start: Option[Instant],
    end: Option[Instant],
    bbox: TwoDimBbox
)
