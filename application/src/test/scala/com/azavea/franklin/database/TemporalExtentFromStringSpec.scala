package com.azavea.franklin.database

import com.azavea.stac4s.TemporalExtent
import org.specs2.Specification

import java.time.Instant
import java.time.Period
import java.sql.Timestamp

class TemporalExtentFromStringSpec extends Specification {
  def is = s2"""
    This specification verifies that temporal extents can be parsed using a number of
    widely used string formats.

    Example cases:
      - datetime=1996-12-19T16:39:57-00:00                                   $testSingleTimeMinus0
      - datetime=1996-12-19T16:39:57+00:00                                   $testSingelTimePlus0
      - datetime=1996-12-19T16:39:57-08:00                                   $testSingleTimeMinus8
      - datetime=1996-12-19T16:39:57+08:00                                   $testSingleTimePlus8
      - datetime=/1985-04-12T23:20:50.52Z                                    $testEmptyStringPreSlash
      - datetime=1985-04-12T23:20:50.52Z/                                    $testEmptyStringPostSlash
      - datetime=1985-04-12T23:20:50.52+01:00/1986-04-12T23:20:50.52Z+01:00  $testTwoTimesBothPluses
      - datetime=1985-04-12T23:20:50.52-01:00/1986-04-12T23:20:50.52Z-01:00  $testTwoTimesBothMinuses
      - datetime=1937-01-01T12:00:27.87+01:00                                $testTimeWithSecondsFraction
      - datetime=1937-01-01T12:00:27.8710+01:00                              $testTimeWithFourDecimalSeconds
      - datetime=1937-01-01T12:00:27.8+01:00                                 $testTimeWithSingleDecimalSeconds
      - datetime=2020-07-23T00:00:00.000+03:00                               $testTimeWithThreeDecimalSeconds
      - datetime=1985-04-12T23:20:50.Z                                       $testTrailingDecimal
    """

  private def testExtentString(s: String, expectedExtent: TemporalExtent) =
    temporalExtentFromString(s) must_== Right(expectedExtent)

  def testSingleTimeMinus0 = {
    val instant = Instant.parse("1996-12-19T16:39:57Z")
    testExtentString(
      "1996-12-19T16:39:57-00:00",
      TemporalExtent(
        Some(instant),
        Some(instant)
      )
    )
  }

  def testSingelTimePlus0 = {
    val instant = Instant.parse("1996-12-19T16:39:57Z")
    testExtentString("1996-12-19T16:39:57+00:00", TemporalExtent(Some(instant), Some(instant)))
  }

  def testSingleTimeMinus8 = {
    val instant = Instant.parse("1996-12-20T00:39:57Z")
    testExtentString(
      "1996-12-19T16:39:57-08:00",
      TemporalExtent(Some(instant), Some(instant))
    )
  }

  def testSingleTimePlus8 = {
    val instant = Instant.parse("1996-12-19T08:39:57Z")
    testExtentString("1996-12-19T16:39:57+08:00", TemporalExtent(Some(instant), Some(instant)))
  }

  def testEmptyStringPreSlash = {
    val instant = Instant.parse("1985-04-12T23:20:50.52Z")
    testExtentString("/1985-04-12T23:20:50.52Z", TemporalExtent(None, Some(instant)))
  }

  def testEmptyStringPostSlash = {
    val instant = Instant.parse("1985-04-12T23:20:50.52Z")
    testExtentString("1985-04-12T23:20:50.52Z/", TemporalExtent(Some(instant), None))
  }

  def testTwoTimesBothPluses = {
    val start = Instant.parse("1985-04-12T22:20:50.52Z")
    val end   = Instant.parse("1986-04-12T22:20:50.52Z")
    testExtentString(
      "1985-04-12T23:20:50.52+01:00/1986-04-12T23:20:50.52+01:00",
      TemporalExtent(Some(start), Some(end))
    )
  }

  def testTwoTimesBothMinuses = {
    val start = Instant.parse("1985-04-13T00:20:50.52Z")
    val end   = Instant.parse("1986-04-13T00:20:50.52Z")
    testExtentString(
      "1985-04-12T23:20:50.52-01:00/1986-04-12T23:20:50.52-01:00",
      TemporalExtent(Some(start), Some(end))
    )
  }

  def testTimeWithSecondsFraction = {
    val instant = Instant.parse("1937-01-01T11:00:27.87Z")
    testExtentString("1937-01-01T12:00:27.87+01:00", TemporalExtent(Some(instant), Some(instant)))
  }

  def testTimeWithFourDecimalSeconds = {
    val instant = Instant.parse("1937-01-01T11:00:27.8710Z")
    testExtentString("1937-01-01T12:00:27.8710+01:00", TemporalExtent(Some(instant), Some(instant)))
  }

  def testTimeWithSingleDecimalSeconds = {
    val instant = Instant.parse("1937-01-01T11:00:27.8Z")
    testExtentString("1937-01-01T12:00:27.8+01:00", TemporalExtent(Some(instant), Some(instant)))
  }

  def testTimeWithThreeDecimalSeconds = {
    val instant = Instant.parse("2020-07-22T21:00:00.000Z")
    testExtentString("2020-07-23T00:00:00.000+03:00", TemporalExtent(Some(instant), Some(instant)))

  }

  def testTrailingDecimal = {
    val instant = Instant.parse("1985-04-12T23:20:50.Z")
    testExtentString("1985-04-12T23:20:50.Z", TemporalExtent(Some(instant), Some(instant)))
  }

}
