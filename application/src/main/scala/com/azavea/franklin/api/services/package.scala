package com.azavea.franklin.api

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.net.URLDecoder

package object services {
  def urlEncode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  def urlDecode(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.toString)
}
