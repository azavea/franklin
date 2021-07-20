package com.azavea.franklin.datamodel

import cats.effect.IO
import cats.syntax.all._
import com.azavea.franklin.Generators
import com.azavea.franklin.api.TestImplicits
import com.azavea.stac4s.StacItem
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.testing.JvmInstances._
import com.azavea.stac4s.testing._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.testing.{ArbitraryInstances, CodecTests}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.specs2.{ScalaCheck, Specification}
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

import scala.concurrent.ExecutionContext.Implicits.global

class SerDeSpec
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Generators
    with Checkers
    with ArbitraryInstances {
  checkAll("Codec.MapCenter", CodecTests[MapCenter].unserializableCodec)
}
