package com.azavea.franklin.datamodel

import com.azavea.stac4s.TwoDimBbox

import java.time.Instant

final case class BulkExtent(
    start: Option[Instant],
    end: Option[Instant],
    bbox: TwoDimBbox
)
