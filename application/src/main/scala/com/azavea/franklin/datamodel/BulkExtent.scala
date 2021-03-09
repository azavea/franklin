package com.azavea.franklin.datamodel

import java.time.Instant
import com.azavea.stac4s.Bbox

final case class BulkExtent(
    start: Option[Instant],
    end: Option[Instant],
    bbox: Option[Bbox]
)
