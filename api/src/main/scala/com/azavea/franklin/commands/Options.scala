package com.azavea.franklin.api.commands

import com.monovore.decline.Opts

object Options extends DatabaseOptions with ApiOptions {

  val catalogRoot: Opts[String] = Opts
    .option[String]("catalog-root", "Root of STAC catalog to import")
}
