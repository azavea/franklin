package com.azavea.franklin.api

import sttp.tapir.generic.Configuration

package object endpoints {
  implicit val derivationConfig = Configuration.default.withSnakeCaseMemberNames
}
