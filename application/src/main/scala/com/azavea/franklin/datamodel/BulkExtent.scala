package com.azavea.franklin.datamodel

import com.azavea.stac4s.Bbox

import java.time.Instant

final case class BulkExtent(
    start: Option[Instant],
    end: Option[Instant],
    bbox: Option[Bbox]
)
