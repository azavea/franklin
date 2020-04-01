package com.azavea.franklin

import com.azavea.stac4s.{StacLink, StacLinkType}

package object crawler {

  // filters links out of a list that need to be rewritten on import
  def filterLinks(links: List[StacLink]): List[StacLink] = {
    links.filter { link =>
      link.rel match {
        case StacLinkType.Child | StacLinkType.Collection | StacLinkType.Parent | StacLinkType.StacRoot | StacLinkType.Self | StacLinkType.Item      => false
        case _                       => true
      }
    }
  }
}
