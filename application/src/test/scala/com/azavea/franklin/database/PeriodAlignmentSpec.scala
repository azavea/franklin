package com.azavea.franklin.api.schemas

import com.azavea.franklin.Generators
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.datamodel.PaginationToken
import org.specs2.{ScalaCheck, Specification}
import org.threeten.extra.PeriodDuration
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult

import java.time.Instant
import java.time.Period

class PeriodAlignmentSpec extends Specification with ScalaCheck {
  def is = s2"""
    This specification verifies that period alignment tests work as expected for a
    few hand-chosen values. These values are hand-chosen because the prior likelihood
    of choosing an Instant at random and a PeriodDuration at random and having
    them line up is quite low.

    Example cases:
      - Annual period from Jan. 1                      $annualJan1Expectation
      - Annual period from a mid-year date             $annualMidYearExpectation 
      - Monthly period from the first of the month     $monthlyDay1Expectation
      - Monthly period from a mid-month date           $monthlyMidMonthExpectation
      - Monthly period from the 31st of a month        $monthly31stExpectation
      - Annual + monthly period                        $comboMonthYearExpectation
      - Monthly + weekly period                        $comboMonthWeekExpectation
      - Self alignment no matter the period            $selfAlignmentExpectation
    """

  private def expectAlignment(inst1: Instant, inst2: Instant, period: PeriodDuration) =
    StacItemDao.periodAligned(inst1, inst2, period) &&
      StacItemDao.periodAligned(inst2, inst1, period)

  def annualJan1Expectation = {
    val janFirst2000 = Instant.parse("2000-01-01T00:00:00Z")
    val janFirst2021 = Instant.parse("2021-01-01T00:00:00Z")
    val annualPeriod = PeriodDuration.parse("P1Y")

    expectAlignment(janFirst2000, janFirst2021, annualPeriod)
  }

  def annualMidYearExpectation = {
    val midYearDate2000 = Instant.parse("2000-04-19T00:00:00Z")
    val midYearDate2021 = Instant.parse("2021-04-19T00:00:00Z")
    val annualPeriod    = PeriodDuration.parse("P1Y")

    expectAlignment(midYearDate2021, midYearDate2000, annualPeriod)
  }

  def monthlyDay1Expectation = {
    val aprilFirst2021 = Instant.parse("2021-04-01T00:00:00Z")
    val julyFirst2020  = Instant.parse("2020-07-01T00:00:00Z")
    val monthlyPeriod  = PeriodDuration.parse("P1M")

    expectAlignment(aprilFirst2021, julyFirst2020, monthlyPeriod)
  }

  def monthlyMidMonthExpectation = {
    val midApril2021  = Instant.parse("2021-04-19T00:00:00Z")
    val midJan2003    = Instant.parse("2003-01-19T00:00:00Z")
    val monthlyPeriod = PeriodDuration.parse("P1M")

    expectAlignment(midApril2021, midJan2003, monthlyPeriod)
  }

  def monthly31stExpectation = {
    val jan312021     = Instant.parse("2021-01-31T00:00:00Z")
    val feb282021     = Instant.parse("2021-02-28T00:00:00Z")
    val monthlyPeriod = PeriodDuration.parse("P1M")

    StacItemDao.periodAligned(jan312021, feb282021, monthlyPeriod) &&
    StacItemDao.periodAligned(feb282021, jan312021, monthlyPeriod)
  }

  def comboMonthYearExpectation = {
    val jan312021           = Instant.parse("2021-01-31T00:00:00Z")
    val sept302025          = Instant.parse("2025-09-30T00:00:00Z")
    val monthlyAnnualPeriod = PeriodDuration.parse("P1Y2M")

    expectAlignment(jan312021, sept302025, monthlyAnnualPeriod)
  }

  def comboMonthWeekExpectation = {
    val jan52021        = Instant.parse("2021-01-05T00:00:00Z")
    val feb122021       = Instant.parse("2021-02-12T00:00:00Z")
    val june22021       = Instant.parse("2021-06-02T00:00:00Z")
    val monthWeekPeriod = PeriodDuration.parse("P1M1W")

    expectAlignment(jan52021, feb122021, monthWeekPeriod) &&
    expectAlignment(jan52021, june22021, monthWeekPeriod)
  }

  def selfAlignmentExpectation = {
    val now           = Instant.now
    val annualPeriod  = PeriodDuration.parse("P1Y")
    val monthlyPeriod = PeriodDuration.parse("P1Y1M")
    val weeklyPeriod  = PeriodDuration.parse("P1Y1M1W")

    expectAlignment(now, now, annualPeriod) &&
    expectAlignment(now, now, monthlyPeriod) &&
    expectAlignment(now, now, weeklyPeriod)
  }
}
