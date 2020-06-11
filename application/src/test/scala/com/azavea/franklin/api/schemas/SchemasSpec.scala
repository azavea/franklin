package com.azavea.franklin.api.schemas

import com.azavea.franklin.datamodel.PaginationToken
import com.azavea.franklin.Generators
import org.specs2.{ScalaCheck, Specification}
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult

class SchemasSpec extends Specification with ScalaCheck with Generators {

  def is = s2"""
  This specification verifies that the custom schemas pass basic round trip tests

  Custom schemas should round trip:
    - PaginationTokens         $paginationTokenExpectation
"""

  def paginationTokenExpectation = prop { (token: PaginationToken) =>
    val codec = implicitly[Codec[String, PaginationToken, TextPlain]]
    codec.decode(codec.encode(token)) ==== DecodeResult.Value(token)
  }
}
