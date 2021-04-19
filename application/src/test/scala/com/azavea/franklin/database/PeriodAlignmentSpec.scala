package com.azavea.franklin.api.schemas

import com.azavea.franklin.database.StacItemDao

import com.azavea.franklin.Generators
import com.azavea.franklin.datamodel.PaginationToken
import org.specs2.{ScalaCheck, Specification}
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult

import java.time.Instant
import org.threeten.extra.PeriodDuration
import java.time.Period

class PeriodAlignmentSpec extends Specification with ScalaCheck with Generators {
  def is = s2"""
    This specification verifies that period alignment tests work as expected for a
    few hand-chosen values. These values are hand-chosen because the prior likelihood
    of choosing an Instant at random and a PeriodDuration at random and having
    them line up is quite low.

    Example cases:
      - Annual period from Jan. 1                      $annualJan1Expectation
      - Annual period from a mid-year date             $annualMidYearExpectation 
      - Monthly period from the first of the month     $monthlyDay1Expectation
      - Monthly period from a mid-month date           monthlyMidMonthExpectation
      - Monthly period from the 31st of a month        monthly31stExpectation
    """

  def annualJan1Expectation = {
    val janFirst2000 = Instant.parse("2000-01-01T00:00:00Z")
    val janFirst2021 = Instant.parse("2021-01-01T00:00:00Z")
    val annualPeriod = PeriodDuration.parse("P1Y")

    StacItemDao.periodAligned(janFirst2000, janFirst2021, annualPeriod) &&
    StacItemDao.periodAligned(janFirst2021, janFirst2000, annualPeriod)
  }

  def annualMidYearExpectation = {
    val midYearDate2000 = Instant.parse("2000-04-19T00:00:00Z")
    val midYearDate2021 = Instant.parse("2021-04-19T00:00:00Z")
    val annualPeriod    = PeriodDuration.parse("P1Y")

    StacItemDao.periodAligned(midYearDate2000, midYearDate2021, annualPeriod) &&
    StacItemDao.periodAligned(midYearDate2021, midYearDate2000, annualPeriod)
  }

  def monthlyDay1Expectation = {
    val aprilFirst2021 = Instant.parse("2021-04-01T00:00:00Z")
    val julyFirst2020  = Instant.parse("2020-07-01T00:00:00Z")
    val monthlyPeriod  = PeriodDuration.parse("P1M")

    StacItemDao.periodAligned(aprilFirst2021, julyFirst2020, monthlyPeriod) &&
    StacItemDao.periodAligned(julyFirst2020, aprilFirst2021, monthlyPeriod)
  }
}
