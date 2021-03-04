package com.azavea.franklin.api

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

package object services {
  def urlEncode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  def urlDecode(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.toString)
}
