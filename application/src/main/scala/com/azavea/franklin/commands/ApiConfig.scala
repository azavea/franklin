package com.azavea.franklin.commands

import eu.timepit.refined.types.numeric.PosInt
import com.azavea.franklin.datamodel.hierarchy.StacHierarchy

case class ApiConfig(
    publicPort: PosInt,
    internalPort: PosInt,
    host: String,
    path: Option[String],
    scheme: String,
    defaultLimit: Int,
    enableTransactions: Boolean,
    stacHierarchy: StacHierarchy
) {

  def getHost(port: PosInt, host: String, scheme: String, path: String): String = {
    (port.value, scheme) match {
      case (443, "https") => s"$scheme://$host$path"
      case (80, "http")   => s"$scheme://$host$path"
      case _              => s"$scheme://$host:$port$path"
    }
  }

  val apiHost: String =
    getHost(publicPort, host, scheme, path map { s =>
      s"/${s.stripPrefix("/").stripSuffix("/")}"
    } getOrElse "")

}
