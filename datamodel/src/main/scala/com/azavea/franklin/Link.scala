package com.azavea.franklin.datamodel

import geotrellis.server.stac.{StacLinkType, StacMediaType}

case class Link(href: String,
                rel: StacLinkType,
                _type: Option[StacMediaType],
                title: Option[String])
