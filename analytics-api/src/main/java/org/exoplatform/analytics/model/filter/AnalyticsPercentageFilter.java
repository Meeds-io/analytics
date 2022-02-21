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

import static org.exoplatform.analytics.model.filter.search.AnalyticsFieldFilterType.RANGE;

import java.time.*;
import java.util.*;

import org.exoplatform.analytics.model.filter.aggregation.*;
import org.exoplatform.analytics.model.filter.search.AnalyticsFieldFilter;

import lombok.*;
import lombok.EqualsAndHashCode.Exclude;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsPercentageFilter extends AbstractAnalyticsFilter {

  private static final long             serialVersionUID    = 5699550622069979910L;

  private String                        chartType;

  private List<String>                  colors;

  private String                        periodType;

  private AnalyticsPeriod               customPeriod;

  private LocalDate                     periodDate;

  private AnalyticsPercentageItemFilter value;

  private AnalyticsPercentageItemFilter threshold;

  private AnalyticsPercentageLimit      percentageLimit     = null;

  @Exclude
  private long                          currentPeriodLimit  = 0;

  @Exclude
  private long                          previousPeriodLimit = 0;

  private String                        lang                = null;

  public AnalyticsPercentageFilter(String title, // NOSONAR
                                   String timeZone,
                                   String chartType,
                                   List<String> colors,
                                   String periodType,
                                   AnalyticsPeriod clonedAnalyticsPeriod,
                                   LocalDate clonedPeriodDate,
                                   AnalyticsPercentageItemFilter cloneAnalyticsPercentageItemFilterValue,
                                   AnalyticsPercentageItemFilter cloneAnalyticsPercentageItemFilterThreshold,
                                   AnalyticsPercentageLimit clonedAnalyticsPercentageLimit,
                                   long currentPeriodLimit,
                                   long previousPeriodLimit,
                                   String lang) {
    this(chartType,
         colors,
         periodType,
         clonedAnalyticsPeriod,
         clonedPeriodDate,
         cloneAnalyticsPercentageItemFilterValue,
         cloneAnalyticsPercentageItemFilterThreshold,
         clonedAnalyticsPercentageLimit,
         currentPeriodLimit,
         previousPeriodLimit,
         lang);
    setTitle(title);
    setTimeZone(timeZone);
  }

  public AnalyticsPeriodType getAnalyticsPeriodType() {
    return AnalyticsPeriodType.periodTypeByName(periodType);
  }

  public String getAnalyticsPeriodInterval() {
    AnalyticsPeriodType analyticsPeriodType = getAnalyticsPeriodType();
    if (analyticsPeriodType != null && periodDate != null) {
      return analyticsPeriodType.getInterval();
    } else if (customPeriod != null) {
      return customPeriod.getInterval();
    }
    return null;
  }

  public AnalyticsPeriod getCurrentAnalyticsPeriod() {
    AnalyticsPeriodType analyticsPeriodType = getAnalyticsPeriodType();
    if (analyticsPeriodType != null && periodDate != null) {
      return analyticsPeriodType.getCurrentPeriod(periodDate, zoneId());
    } else if (customPeriod != null) {
      return customPeriod.clone();
    }
    return null;
  }

  public AnalyticsPeriod getPreviousAnalyticsPeriod() {
    AnalyticsPeriodType analyticsPeriodType = getAnalyticsPeriodType();
    if (analyticsPeriodType != null && periodDate != null) {
      return analyticsPeriodType.getPreviousPeriod(periodDate, zoneId());
    } else if (customPeriod != null) {
      return customPeriod.previousPeriod();
    }
    return null;
  }

  public void setPeriodDateInMS(long timestampInMS) {
    periodDate = Instant.ofEpochMilli(timestampInMS).atZone(zoneId()).toLocalDate();
  }

  public AnalyticsFilter computeValueFilter() {
    return computeValueFilter(null, 0);
  }

  public AnalyticsFilter computeValueFilter(AnalyticsPeriod period, long limit) {
    List<AnalyticsAggregation> xAxisAggregations = new ArrayList<>();

    if (period == null) {
      AnalyticsAggregation xAxisAggregation = getXAxisAggregation();
      if (xAxisAggregation != null) {
        xAxisAggregations.add(xAxisAggregation);
      }
    }

    if (limit > 0) {
      AnalyticsAggregation limitAggregation = new AnalyticsAggregation(percentageLimit.getField());
      limitAggregation.setType(AnalyticsAggregationType.TERMS);
      limitAggregation.setLimit(limit);
      xAxisAggregations.add(limitAggregation);
    }

    return new AnalyticsFilter(getTitle(),
                               getTimeZone(),
                               chartType,
                               colors,
                               getValueFilters(period),
                               null,
                               xAxisAggregations,
                               getValueYAggregation(),
                               lang,
                               0l,
                               0l);
  }

  public AnalyticsFilter computeThresholdFilter() {
    AnalyticsAggregation xAxisAggregation = getXAxisAggregation();
    return new AnalyticsFilter(getTitle(),
                               getTimeZone(),
                               chartType,
                               colors,
                               getThresholdFilters(),
                               null,
                               xAxisAggregation == null ? Collections.emptyList()
                                                        : Collections.singletonList(xAxisAggregation),
                               getThresholdYAggregation(),
                               lang,
                               0l,
                               0l);
  }

  public AnalyticsFilter computeLimitFilter() {
    AnalyticsAggregation xAxisAggregation = getXAxisAggregation();
    return new AnalyticsFilter(getTitle(),
                               getTimeZone(),
                               chartType,
                               colors,
                               getLimitFilters(),
                               null,
                               xAxisAggregation == null ? Collections.emptyList()
                                                        : Collections.singletonList(xAxisAggregation),
                               getLimitYAggregation(),
                               lang,
                               0l,
                               0l);
  }

  @Override
  public AnalyticsPercentageFilter clone() { // NOSONAR
    LocalDate clonedPeriodDate = periodDate == null ? null : LocalDate.from(periodDate);
    AnalyticsPeriod clonedAnalyticsPeriod = customPeriod == null ? null : customPeriod.clone();
    AnalyticsPercentageItemFilter cloneAnalyticsPercentageItemFilterValue = value == null ? null : value.clone();
    AnalyticsPercentageItemFilter cloneAnalyticsPercentageItemFilterThreshold = threshold == null ? null
                                                                                                  : threshold.clone();
    AnalyticsPercentageLimit clonedAnalyticsPercentageLimit = percentageLimit == null ? null
                                                                                      : percentageLimit.clone();
    return new AnalyticsPercentageFilter(getTitle(),
                                         getTimeZone(),
                                         chartType,
                                         colors,
                                         periodType,
                                         clonedAnalyticsPeriod,
                                         clonedPeriodDate,
                                         cloneAnalyticsPercentageItemFilterValue,
                                         cloneAnalyticsPercentageItemFilterThreshold,
                                         clonedAnalyticsPercentageLimit,
                                         currentPeriodLimit,
                                         previousPeriodLimit,
                                         lang);
  }

  private AnalyticsAggregation getXAxisAggregation() {
    String interval = getAnalyticsPeriodInterval();
    if (interval == null) {
      return null;
    }
    AnalyticsAggregation xAxisAggregation = new AnalyticsAggregation();
    xAxisAggregation.setField("timestamp");
    xAxisAggregation.setSortDirection("DESC");
    xAxisAggregation.setType(AnalyticsAggregationType.DATE);
    xAxisAggregation.setInterval(interval);
    xAxisAggregation.setMinBound(getPreviousAnalyticsPeriod().getFromInMS());
    xAxisAggregation.setMaxBound(getCurrentAnalyticsPeriod().getToInMS() - 1000);
    xAxisAggregation.setUseBounds(true);
    if (customPeriod != null) {
      long diffInDays = customPeriod.getDiffInDays();
      if (diffInDays > 0) {
        long offset = (xAxisAggregation.getMinBound() / 86400000l) % diffInDays;
        if (offset > 0) {
          xAxisAggregation.setOffset(offset + "d");
        }
      }
    } else {
      AnalyticsPeriodType analyticsPeriodType = getAnalyticsPeriodType();
      if (analyticsPeriodType != null && analyticsPeriodType.getOffset(xAxisAggregation.getMinBound()) > 0) {
        long offset = analyticsPeriodType.getOffset(xAxisAggregation.getMinBound());
        xAxisAggregation.setOffset(offset + "d");
      }
    }
    return xAxisAggregation;
  }

  private AnalyticsAggregation getValueYAggregation() {
    return value == null ? null : value.getYAxisAggregation().clone();
  }

  private AnalyticsAggregation getLimitYAggregation() {
    return percentageLimit == null
        || percentageLimit.getAggregation() == null
        || percentageLimit.getAggregation().getYAxisAggregation() == null
                                                                          ? null
                                                                          : percentageLimit.getAggregation()
                                                                                           .getYAxisAggregation()
                                                                                           .clone();
  }

  private AnalyticsAggregation getThresholdYAggregation() {
    return threshold == null ? null : threshold.getYAxisAggregation().clone();
  }

  private List<AnalyticsFieldFilter> getValueFilters(AnalyticsPeriod period) {
    List<AnalyticsFieldFilter> filters = new ArrayList<>();

    AnalyticsFieldFilter periodFilter = getPeriodFilter(period);
    if (periodFilter != null) {
      filters.add(periodFilter);
    }
    if (value != null && value.getFilters() != null) {
      filters.addAll(value.getFilters());
    }
    return filters;
  }

  private List<AnalyticsFieldFilter> getThresholdFilters() {
    List<AnalyticsFieldFilter> filters = new ArrayList<>();

    AnalyticsFieldFilter periodFilter = getPeriodFilter(null);
    if (periodFilter != null) {
      filters.add(periodFilter);
    }
    if (threshold != null && threshold.getFilters() != null) {
      filters.addAll(threshold.getFilters());
    }
    return filters;
  }

  private List<AnalyticsFieldFilter> getLimitFilters() {
    List<AnalyticsFieldFilter> filters = new ArrayList<>();

    AnalyticsFieldFilter periodFilter = getPeriodFilter(null);
    if (periodFilter != null) {
      filters.add(periodFilter);
    }
    if (percentageLimit != null && percentageLimit.getAggregation() != null
        && percentageLimit.getAggregation().getFilters() != null) {
      filters.addAll(percentageLimit.getAggregation().getFilters());
    }
    return filters;
  }

  private AnalyticsFieldFilter getPeriodFilter(AnalyticsPeriod period) {
    if (period == null) {
      AnalyticsPeriod currentAnalyticsPeriod = getCurrentAnalyticsPeriod();
      AnalyticsPeriod previousAnalyticsPeriod = getPreviousAnalyticsPeriod();
      if (previousAnalyticsPeriod == null || currentAnalyticsPeriod == null) {
        return null;
      }
      period = new AnalyticsPeriod(previousAnalyticsPeriod.getFrom(), currentAnalyticsPeriod.getTo());
    }
    AnalyticsFilter.Range rangeFilter = new AnalyticsFilter.Range(period.getFromInMS(),
                                                                  period.getToInMS());
    return new AnalyticsFieldFilter("timestamp", RANGE, rangeFilter);
  }

}
