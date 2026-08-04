/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.analytics.model.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

import org.junit.Test;

public class AnalyticsPeriodTypeTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Paris");

  private final LocalDate     today     = LocalDate.now(TIME_ZONE);

  @Test
  public void testCurrentPeriodInProgressEndsAtTheEndOfTheCurrentDay() {
    for (AnalyticsPeriodType periodType : AnalyticsPeriodType.values()) {
      AnalyticsPeriod currentPeriod = periodType.getCurrentPeriod(today, TIME_ZONE);
      assertEquals(periodType + " must not be measured after the current day",
                   today.plusDays(1),
                   currentPeriod.getTo());
    }
  }

  @Test
  public void testCurrentPeriodStartsOnTheFirstDayOfThePeriod() {
    assertEquals(today, AnalyticsPeriodType.TODAY.getCurrentPeriod(today, TIME_ZONE).getFrom());
    assertEquals(DayOfWeek.MONDAY,
                 AnalyticsPeriodType.THIS_WEEK.getCurrentPeriod(today, TIME_ZONE).getFrom().getDayOfWeek());
    assertEquals(today.withDayOfMonth(1), AnalyticsPeriodType.THIS_MONTH.getCurrentPeriod(today, TIME_ZONE).getFrom());
    assertEquals(today.getMonth().firstMonthOfQuarter(),
                 AnalyticsPeriodType.THIS_QUARTER.getCurrentPeriod(today, TIME_ZONE).getFrom().getMonth());
    assertEquals(today.getMonthValue() > 6 ? Month.JULY : Month.JANUARY,
                 AnalyticsPeriodType.THIS_SEMESTER.getCurrentPeriod(today, TIME_ZONE).getFrom().getMonth());
    assertEquals(today.withDayOfYear(1), AnalyticsPeriodType.THIS_YEAR.getCurrentPeriod(today, TIME_ZONE).getFrom());
  }

  @Test
  public void testCompletedPeriodKeepsItsWholeRange() {
    LocalDate dateOfACompletedMonth = today.minusMonths(2).withDayOfMonth(1);
    AnalyticsPeriod currentPeriod = AnalyticsPeriodType.THIS_MONTH.getCurrentPeriod(dateOfACompletedMonth, TIME_ZONE);
    assertEquals(dateOfACompletedMonth, currentPeriod.getFrom());
    assertEquals(dateOfACompletedMonth.plusMonths(1), currentPeriod.getTo());

    LocalDate dateOfACompletedYear = today.minusYears(1).withDayOfYear(1);
    currentPeriod = AnalyticsPeriodType.THIS_YEAR.getCurrentPeriod(dateOfACompletedYear, TIME_ZONE);
    assertEquals(dateOfACompletedYear, currentPeriod.getFrom());
    assertEquals(dateOfACompletedYear.plusYears(1), currentPeriod.getTo());
  }

  @Test
  public void testPreviousPeriodIsTheEqualWindowPrecedingTheCurrentOne() {
    for (AnalyticsPeriodType periodType : AnalyticsPeriodType.values()) {
      AnalyticsPeriod currentPeriod = periodType.getCurrentPeriod(today, TIME_ZONE);
      AnalyticsPeriod previousPeriod = periodType.getPreviousPeriod(today, TIME_ZONE);
      assertEquals(periodType + " previous period must end when the current one starts",
                   currentPeriod.getFromInMS(),
                   previousPeriod.getToInMS());
      assertEquals(periodType + " previous period must cover the same elapsed duration",
                   currentPeriod.getFromInMS() - currentPeriod.getDurationInMS(),
                   previousPeriod.getFromInMS());
      // Mandatory to aggregate both periods in two exact Elasticsearch buckets
      assertTrue(periodType + " periods must be contiguous and of the same duration",
                 currentPeriod.isContiguousTo(previousPeriod));
    }
  }

  @Test
  public void testMonthInProgressIsComparedWithTheSameNumberOfDaysBeforeIt() {
    AnalyticsPeriod currentPeriod = AnalyticsPeriodType.THIS_MONTH.getCurrentPeriod(today, TIME_ZONE);
    AnalyticsPeriod previousPeriod = AnalyticsPeriodType.THIS_MONTH.getPreviousPeriod(today, TIME_ZONE);
    LocalDate firstDayOfMonth = today.withDayOfMonth(1);

    // The 4th of a month is measured on its 4 elapsed days, and not on the whole
    // month, which used to be compared with a complete previous month
    assertEquals(firstDayOfMonth, currentPeriod.getFrom());
    assertEquals(today.getDayOfMonth(), currentPeriod.getDiffInDays());
    assertEquals(firstDayOfMonth, previousPeriod.getTo());
    // Compared in milliseconds: a daylight saving time change in the elapsed
    // days shifts the previous window by one hour
    assertEquals(currentPeriod.getDurationInMS(), previousPeriod.getDurationInMS());
  }

}
