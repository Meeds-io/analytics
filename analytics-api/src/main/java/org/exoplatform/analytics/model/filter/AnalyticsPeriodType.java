/**
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2022 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.exoplatform.analytics.model.filter;

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
        return new AnalyticsPeriod(date, date.plusDays(1), interval, timeZone);
      case THIS_WEEK:
        start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        end = start.plusDays(7);
        return new AnalyticsPeriod(start, end, interval, timeZone);
      case THIS_MONTH:
        start = date.withDayOfMonth(1);
        end = start.plusMonths(1);
        return new AnalyticsPeriod(start, end, interval, timeZone);
      case THIS_QUARTER:
        start = Year.of(date.getYear()).atMonth(date.getMonth().firstMonthOfQuarter()).atDay(1);
        end = start.plusMonths(3);
        return new AnalyticsPeriod(start, end, interval, timeZone);
      case THIS_SEMESTER:
        start = date.getMonth().compareTo(Month.JUNE) > 0 ? Year.of(date.getYear()).atMonth(Month.JULY).atDay(1)
                                                          : Year.of(date.getYear()).atMonth(Month.JANUARY).atDay(1);
        end = start.plusMonths(6);
        return new AnalyticsPeriod(start, end, interval, timeZone);
      case THIS_YEAR:
        start = date.withDayOfYear(1);
        end = start.plusYears(1);
        return new AnalyticsPeriod(start, end, interval, timeZone);
      default:
        return null;
    }
  }

  public long getOffset(long timestamp) {
    if (this == THIS_SEMESTER) {
      return (timestamp / 86400000l) % 182;
    }
    return 0;
  }

  public AnalyticsPeriod getPreviousPeriod(LocalDate date, ZoneId timeZone) {
    switch (this) {
      case TODAY:
        return getCurrentPeriod(date.minusDays(1), timeZone);
      case THIS_WEEK:
        return getCurrentPeriod(date.minusWeeks(1), timeZone);
      case THIS_MONTH:
        return getCurrentPeriod(date.minusMonths(1), timeZone);
      case THIS_QUARTER:
        return getCurrentPeriod(date.minusMonths(3), timeZone);
      case THIS_SEMESTER:
        AnalyticsPeriod currentPeriod = getCurrentPeriod(date, timeZone);
        return new AnalyticsPeriod(currentPeriod.getFrom().minusDays(182), // NOSONAR
                                   currentPeriod.getTo().minusDays(182),
                                   interval);
      case THIS_YEAR:
        return getCurrentPeriod(date.minusYears(1), timeZone);
      default:
        return null;
    }
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
