/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

public enum AnalyticsPeriodType {
  TODAY("today", "day"),
  THIS_WEEK("thisWeek", "week"),
  THIS_MONTH("thisMonth", "month"),
  THIS_QUARTER("thisQuarter", "quarter"),
  THIS_SEMESTER("thisSemester", "182d"),
  THIS_YEAR("thisYear", "year");

  private String typeName;

  @Getter
  private String interval;

  private AnalyticsPeriodType(String typeName, String interval) {
    this.typeName = typeName;
    this.interval = interval;
  }

  public AnalyticsPeriod getCurrentPeriod(LocalDate date, ZoneId timeZone) {
    if (timeZone == null) {
      timeZone = ZoneOffset.UTC;
    }
    LocalDate start = null;
    LocalDate end = null;
    switch (this) {
      case TODAY:
        start = date;
        end = date.plusDays(1);
        break;
      case THIS_WEEK:
        start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        end = start.plusDays(7);
        break;
      case THIS_MONTH:
        start = date.withDayOfMonth(1);
        end = start.plusMonths(1);
        break;
      case THIS_QUARTER:
        start = Year.of(date.getYear()).atMonth(date.getMonth().firstMonthOfQuarter()).atDay(1);
        end = start.plusMonths(3);
        break;
      case THIS_SEMESTER:
        start = date.getMonth().compareTo(Month.JUNE) > 0 ? Year.of(date.getYear()).atMonth(Month.JULY).atDay(1)
                                                          : Year.of(date.getYear()).atMonth(Month.JANUARY).atDay(1);
        end = start.plusMonths(6);
        break;
      case THIS_YEAR:
        start = date.withDayOfYear(1);
        end = start.plusYears(1);
        break;
      default:
        return null;
    }
    return new AnalyticsPeriod(start, elapsedEnd(start, end, timeZone), interval, timeZone);
  }

  /**
   * A period that is still in progress is measured until the end of the current
   * day only: its days that aren't elapsed yet would else be compared to a
   * complete previous period, which displays for example a -100% evolution on
   * the first days of a month. The end of the current day is kept, and not the
   * current time, so that no statistic of the day is left out.
   *
   * @param start first day of the period
   * @param end day following the last day of the period
   * @param timeZone user {@link ZoneId}
   * @return the end of the elapsed part of the period, which is its own end when
   *         the period is already complete
   */
  private LocalDate elapsedEnd(LocalDate start, LocalDate end, ZoneId timeZone) {
    LocalDate endOfToday = LocalDate.now(timeZone).plusDays(1);
    return endOfToday.isAfter(start) && endOfToday.isBefore(end) ? endOfToday : end;
  }

  public long getOffset(long timestamp) {
    if (this == THIS_SEMESTER) {
      return (timestamp / 86400000l) % 182;
    }
    return 0;
  }

  /**
   * The previous period is the window of the same duration that immediately
   * precedes the current one: a period in progress is compared to the same
   * number of elapsed days, and not to a complete previous period.
   *
   * @param date date included in the current period
   * @param timeZone user {@link ZoneId}
   * @return {@link AnalyticsPeriod} preceding the current period
   */
  public AnalyticsPeriod getPreviousPeriod(LocalDate date, ZoneId timeZone) {
    AnalyticsPeriod currentPeriod = getCurrentPeriod(date, timeZone);
    return currentPeriod == null ? null : currentPeriod.previousPeriod();
  }

  public String getTypeName() {
    return typeName;
  }

  public static AnalyticsPeriodType periodTypeByName(String typeName) {
    return Arrays.stream(values())
                 .filter(value -> StringUtils.equals(value.getTypeName(), typeName))
                 .findFirst()
                 .orElse(null);
  }
}
