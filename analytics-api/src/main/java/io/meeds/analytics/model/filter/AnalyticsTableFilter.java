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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.meeds.analytics.model.filter.AnalyticsFilter.Range;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;

import lombok.*;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTableFilter extends AbstractAnalyticsFilter {

  static final long                        serialVersionUID = 8707611304110081607L;

  private AnalyticsTableColumnFilter       mainColumn       = null;

  private List<AnalyticsTableColumnFilter> columns          = new ArrayList<>();

  private int                              pageSize;

  private String                           sortBy           = null;

  private String                           sortDirection    = null;

  public AnalyticsTableFilter(String title,
                              String timeZone,
                              AnalyticsTableColumnFilter clonedMainColumn,
                              List<AnalyticsTableColumnFilter> clonedColumns,
                              int pageSize,
                              String sortBy,
                              String sortDirection) {
    this(clonedMainColumn, clonedColumns, pageSize, sortBy, sortDirection);
    setTitle(title);
    setTimeZone(timeZone);
  }

  public AnalyticsTableColumnFilter getColumnFilter(int columnIndex) {
    if (columnIndex == 0) {
      return this.mainColumn == null ? null : this.mainColumn.clone();
    } else if (columnIndex <= this.columns.size()) {
      return this.columns.get(columnIndex - 1).clone();
    }
    return null;
  }

  public AnalyticsFilter buildColumnFilter(AnalyticsPeriod period,
                                           AnalyticsPeriodType periodType,
                                           AnalyticsFieldFilter fieldFilter,
                                           int limit,
                                           String sortDirection,
                                           int columnIndex,
                                           boolean isValue) {
    AnalyticsTableColumnFilter column = null;
    if (columnIndex == 0) {
      column = this.mainColumn == null ? null : this.mainColumn.clone();
    } else if (columnIndex <= this.columns.size()) {
      column = this.columns.get(columnIndex - 1).clone();
    }
    if (mainColumn == null) {
      throw new IllegalStateException("Main Column is not set");
    }
    if (mainColumn.getValueAggregation() == null
        || mainColumn.getValueAggregation().getAggregation() == null
        || mainColumn.getValueAggregation().getAggregation().getField() == null) {
      throw new IllegalStateException("Main Column aggregation is not set");
    }
    if (column == null) {
      throw new IllegalStateException("Column with index " + columnIndex + " doesn't exist");
    }
    AnalyticsTableColumnAggregation columnAggregation = isValue ? column.getValueAggregation()
                                                                : column.getThresholdAggregation();

    List<AnalyticsAggregation> xAxisAggregations = new ArrayList<>();
    xAxisAggregations.add(mainColumn.getValueAggregation().getAggregation());
    AnalyticsAggregation yAxisAggregation = null;
    if (columnIndex > 0) {
      yAxisAggregation = columnAggregation.getAggregation();
    }

    List<AnalyticsFieldFilter> filters = columnAggregation.getFilters();
    if (fieldFilter != null) {
      filters.add(fieldFilter);
    }

    if (this.mainColumn.getValueAggregation() != null
        && this.mainColumn.getValueAggregation().getFilters() != null
        && !this.mainColumn.getValueAggregation().getFilters().isEmpty()) {
      filters.addAll(this.mainColumn.getValueAggregation().getFilters());
    }

    if (period != null && !columnAggregation.isPeriodIndependent()) {
      addPeriodFilter(period, periodType, xAxisAggregations, filters, column.isPreviousPeriod());
    }

    AnalyticsAggregation lastAggregation = xAxisAggregations.get(xAxisAggregations.size() - 1);
    if (StringUtils.isNotBlank(sortDirection)) {
      lastAggregation.setSortDirection(sortDirection);
    }
    if (limit > 0) {
      lastAggregation.setLimit(limit);
    }

    return new AnalyticsFilter(null,
                               null,
                               null,
                               filters,
                               null,
                               xAxisAggregations,
                               yAxisAggregation,
                               null,
                               0,
                               limit);
  }

  public AnalyticsPeriod getCurrentPeriod(AnalyticsPeriod period, AnalyticsPeriodType periodType) {
    if (periodType == null) {
      return period;
    } else {
      return periodType.getCurrentPeriod(middleOfPeriod(period), zoneId());
    }
  }

  private LocalDate middleOfPeriod(AnalyticsPeriod period) {
    return Instant.ofEpochMilli(period.getFromInMS() + (period.getToInMS() - period.getFromInMS()) / 2)
                  .atZone(zoneId())
                  .toLocalDate();
  }

  public AnalyticsPeriod getPreviousPeriod(AnalyticsPeriod period, AnalyticsPeriodType periodType) {
    if (periodType == null) {
      return period.previousPeriod();
    } else {
      return periodType.getPreviousPeriod(middleOfPeriod(period), zoneId());
    }
  }

  @Override
  public AnalyticsTableFilter clone() { // NOSONAR
    AnalyticsTableColumnFilter clonedMainColumn = mainColumn == null ? null : mainColumn.clone();
    List<AnalyticsTableColumnFilter> clonedColumns = columns == null ? null
                                                                     : columns.stream()
                                                                              .map(AnalyticsTableColumnFilter::clone)
                                                                              .collect(Collectors.toList());
    return new AnalyticsTableFilter(getTitle(), getTimeZone(), clonedMainColumn, clonedColumns, pageSize, sortBy, sortDirection);
  }

  private void addPeriodFilter(AnalyticsPeriod period,
                               AnalyticsPeriodType periodType,
                               List<AnalyticsAggregation> xAxisAggregations,
                               List<AnalyticsFieldFilter> filters,
                               boolean compareWithPreviousPeriod) {
    long fromInMS = period.getFromInMS();
    long toInMS = period.getToInMS();

    if (compareWithPreviousPeriod) {
      AnalyticsPeriod previousPeriod;
      String interval;
      String offset = null;
      if (periodType == null) {
        previousPeriod = period.previousPeriod();
        fromInMS = previousPeriod.getFromInMS();
        long diffInDays = period.getDiffInDays();
        if (diffInDays > 0) {
          interval = period.getInterval();
          long offsetLong = (fromInMS / 86400000l) % diffInDays;
          if (offsetLong > 0) {
            offset = offsetLong + "d";
          }
        } else {
          interval = "1d";
        }
      } else {
        previousPeriod = periodType.getPreviousPeriod(period.getFrom(), zoneId());
        fromInMS = previousPeriod.getFromInMS();
        interval = periodType.getInterval();
        if (periodType.getOffset(previousPeriod.getFromInMS()) > 0) {
          offset = periodType.getOffset(previousPeriod.getFromInMS()) + "d";
        }
      }
      xAxisAggregations.add(0,
                            new AnalyticsAggregation(AnalyticsAggregationType.DATE,
                                                     "timestamp",
                                                     "asc",
                                                     interval,
                                                     offset,
                                                     2,
                                                     true,
                                                     fromInMS,
                                                     toInMS));
    }
    AnalyticsFieldFilter periodFilter = new AnalyticsFieldFilter("timestamp",
                                                                 AnalyticsFieldFilterType.RANGE,
                                                                 new Range(fromInMS, toInMS));
    filters.add(periodFilter);
  }

}
