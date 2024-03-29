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

import static org.exoplatform.analytics.model.filter.search.AnalyticsFieldFilterType.*;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.model.filter.aggregation.AnalyticsAggregation;
import org.exoplatform.analytics.model.filter.aggregation.AnalyticsAggregationType;
import org.exoplatform.analytics.model.filter.search.AnalyticsFieldFilter;
import org.exoplatform.analytics.utils.AnalyticsUtils;

import lombok.*;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsFilter extends AbstractAnalyticsFilter {

  private static final long          serialVersionUID    = 5699550622069979910L;

  private String                     chartType;

  private List<String>               colors;

  private List<AnalyticsFieldFilter> filters             = new ArrayList<>();

  private String                     multipleChartsField = null;

  private List<AnalyticsAggregation> xAxisAggregations   = new ArrayList<>();

  private AnalyticsAggregation       yAxisAggregation    = null;

  private String                     lang                = null;

  private long                       offset              = 0;

  private long                       limit               = 0;

  public AnalyticsFilter(String title, // NOSONAR
                         String timeZone,
                         String chartType,
                         List<String> colors,
                         List<AnalyticsFieldFilter> cloneFilters,
                         String multipleChartsField,
                         List<AnalyticsAggregation> cloneXAggs,
                         AnalyticsAggregation cloneyAggregation,
                         String lang,
                         long offset,
                         long limit) {
    this(chartType,
         colors,
         cloneFilters,
         multipleChartsField,
         cloneXAggs,
         cloneyAggregation,
         lang,
         offset,
         limit);
    setTitle(title);
    setTimeZone(timeZone);
  }

  public AnalyticsFilter(String title, // NOSONAR
                         String chartType,
                         List<String> colors,
                         List<AnalyticsFieldFilter> cloneFilters,
                         String multipleChartsField,
                         List<AnalyticsAggregation> cloneXAggs,
                         AnalyticsAggregation cloneyAggregation,
                         String lang,
                         long offset,
                         long limit) {
    this(chartType,
         colors,
         cloneFilters,
         multipleChartsField,
         cloneXAggs,
         cloneyAggregation,
         lang,
         offset,
         limit);
    setTitle(title);
  }

  public List<AnalyticsAggregation> getAggregations() {
    List<AnalyticsAggregation> aggregations = new ArrayList<>();

    AnalyticsAggregation multipleChartsAggregation = getMultipleChartsAggregation();
    if (multipleChartsAggregation != null) {
      if (multipleChartsAggregation.getType() == AnalyticsAggregationType.COUNT) {
        multipleChartsAggregation.setType(AnalyticsAggregationType.TERMS);
      }
      aggregations.add(multipleChartsAggregation);
    }

    for (AnalyticsAggregation analyticsAggregation : xAxisAggregations) {
      if (analyticsAggregation.getType() == AnalyticsAggregationType.COUNT) {
        analyticsAggregation.setType(AnalyticsAggregationType.TERMS);
      }
    }
    aggregations.addAll(xAxisAggregations);

    AnalyticsAggregation lastAggregation = aggregations.isEmpty() ? null : aggregations.get(aggregations.size() - 1);
    if (yAxisAggregation != null) {
      if (lastAggregation != null && StringUtils.isBlank(yAxisAggregation.getField())) {
        yAxisAggregation.setField(lastAggregation.getField());
        yAxisAggregation.setType(AnalyticsAggregationType.COUNT);
      }
      aggregations.add(yAxisAggregation);
    }

    return Collections.unmodifiableList(aggregations);
  }

  public AnalyticsAggregation getMultipleChartsAggregation() {
    if (isMultipleCharts()) {
      return new AnalyticsAggregation(multipleChartsField);
    }
    return null;
  }

  public void addXAxisAggregation(AnalyticsAggregation aggregation) {
    xAxisAggregations.add(aggregation);
  }

  public boolean isMultipleCharts() {
    return StringUtils.isNotBlank(multipleChartsField);
  }

  public void addNotEqualFilter(String field, String value) {
    AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field, NOT_EQUAL, String.valueOf(value));
    this.filters.add(fieldFilter);
  }

  public void addEqualFilter(String field, String value) {
    AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field, EQUAL, String.valueOf(value));
    this.filters.add(fieldFilter);
  }

  public void addInSetFilter(String field, String... values) {
    if (values != null && values.length > 0) {
      AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field,
                                                                  IN_SET,
                                                                  StringUtils.join(values, AnalyticsUtils.VALUES_SEPARATOR));
      this.filters.add(fieldFilter);
    }
  }

  public void addNotInSetFilter(String field, String... values) {
    if (values != null && values.length > 0) {
      AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field,
                                                                  NOT_IN_SET,
                                                                  StringUtils.join(values, AnalyticsUtils.VALUES_SEPARATOR));
      this.filters.add(fieldFilter);
    }
  }

  public void addRangeFilter(String field, String start, String end) {
    AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field,
                                                                RANGE,
                                                                new Range(start, end));
    this.filters.add(fieldFilter);
  }

  public void addGreaterFilter(String field, long value) {
    AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field, GREATER, String.valueOf(value));
    this.filters.add(fieldFilter);
  }

  public void addLessFilter(String field, long value) {
    AnalyticsFieldFilter fieldFilter = new AnalyticsFieldFilter(field, LESS, String.valueOf(value));
    this.filters.add(fieldFilter);
  }

  @Data
  @ToString
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Range implements Serializable, Cloneable {

    private static final long serialVersionUID = 570632355720481459L;

    private String            min;

    private String            max;

    public Range(long min, long max) {
      this.min = String.valueOf(min);
      this.max = String.valueOf(max);
    }

    @Override
    public Range clone() { // NOSONAR
      return new Range(min, max);
    }
  }

  @Override
  public AnalyticsFilter clone() { // NOSONAR
    List<AnalyticsFieldFilter> cloneFilters = new ArrayList<>(filters).stream()
                                                                      .map(AnalyticsFieldFilter::clone)
                                                                      .collect(Collectors.toList());
    List<AnalyticsAggregation> cloneXAggs = new ArrayList<>(xAxisAggregations).stream()
                                                                              .map(AnalyticsAggregation::clone)
                                                                              .collect(Collectors.toList());
    AnalyticsAggregation cloneyAggregation = yAxisAggregation == null ? null : yAxisAggregation.clone();
    return new AnalyticsFilter(getTitle(),
                               getTimeZone(),
                               chartType,
                               colors,
                               cloneFilters,
                               multipleChartsField,
                               cloneXAggs,
                               cloneyAggregation,
                               lang,
                               offset,
                               limit);
  }

}
