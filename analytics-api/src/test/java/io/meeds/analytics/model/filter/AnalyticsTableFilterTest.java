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

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.Test;

public class AnalyticsTableFilterTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Paris");

  private final LocalDate     today     = LocalDate.now(TIME_ZONE);

  @Test
  public void testCustomPeriodIsComparedWithTheDaysPrecedingIt() {
    AnalyticsTableFilter tableFilter = newTableFilter();
    AnalyticsPeriod selectedPeriod = new AnalyticsPeriod(today.minusDays(2), today.plusDays(1), "3d", TIME_ZONE);

    AnalyticsPeriod currentPeriod = tableFilter.getCurrentPeriod(selectedPeriod, null);
    AnalyticsPeriod previousPeriod = tableFilter.getPreviousPeriod(selectedPeriod, null);

    assertEquals(selectedPeriod.getFromInMS(), currentPeriod.getFromInMS());
    assertEquals(selectedPeriod.getToInMS(), currentPeriod.getToInMS());
    assertEquals(today.minusDays(2), previousPeriod.getTo());
    // Compared in milliseconds: a daylight saving time change in the selected
    // days shifts the previous window by one hour
    assertEquals(currentPeriod.getFromInMS() - currentPeriod.getDurationInMS(), previousPeriod.getFromInMS());
    assertTrue(currentPeriod.isContiguousTo(previousPeriod));
  }

  @Test
  public void testPeriodTypeIsComparedLikeTheSameDaysSelectedManually() {
    AnalyticsTableFilter tableFilter = newTableFilter();
    LocalDate firstDayOfMonth = today.withDayOfMonth(1);
    // What the date picker sends when 'This month' is selected: the elapsed days
    // of the month, the period type being sent apart
    AnalyticsPeriod selectedPeriod = new AnalyticsPeriod(firstDayOfMonth, today.plusDays(1), "month", TIME_ZONE);

    AnalyticsPeriod currentPeriod = tableFilter.getCurrentPeriod(selectedPeriod, AnalyticsPeriodType.THIS_MONTH);
    AnalyticsPeriod previousPeriod = tableFilter.getPreviousPeriod(selectedPeriod, AnalyticsPeriodType.THIS_MONTH);

    // The measured period is the one displayed by the date picker, and not the
    // whole calendar month
    assertEquals(selectedPeriod.getFromInMS(), currentPeriod.getFromInMS());
    assertEquals(selectedPeriod.getToInMS(), currentPeriod.getToInMS());
    // Same comparison as a manual selection of those days
    assertEquals(tableFilter.getPreviousPeriod(selectedPeriod, null).getFromInMS(), previousPeriod.getFromInMS());
    assertEquals(tableFilter.getPreviousPeriod(selectedPeriod, null).getToInMS(), previousPeriod.getToInMS());
    assertTrue(currentPeriod.isContiguousTo(previousPeriod));
  }

  private AnalyticsTableFilter newTableFilter() {
    AnalyticsTableFilter tableFilter = new AnalyticsTableFilter();
    tableFilter.setTimeZone(TIME_ZONE.getId());
    return tableFilter;
  }

}
