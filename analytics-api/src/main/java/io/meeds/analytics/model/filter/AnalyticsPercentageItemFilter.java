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

import static io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.utils.AnalyticsUtils;

import groovy.transform.ToString;
import lombok.*;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsPercentageItemFilter implements Serializable, Cloneable {

  private static final long          serialVersionUID = 2412056356203118469L;

  private List<AnalyticsFieldFilter> filters          = new ArrayList<>();

  private AnalyticsAggregation       yAxisAggregation = null;

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
                                                                new AnalyticsFilter.Range(start, end));
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

    @Override
    public AnalyticsFilter.Range clone() { // NOSONAR
      return new AnalyticsFilter.Range(min, max);
    }
  }

  @Override
  public AnalyticsPercentageItemFilter clone() { // NOSONAR
    List<AnalyticsFieldFilter> clonedFilters = new ArrayList<>(filters).stream()
                                                                       .map(AnalyticsFieldFilter::clone)
                                                                       .collect(Collectors.toList());
    AnalyticsAggregation cloneyAggregation = yAxisAggregation == null ? null : yAxisAggregation.clone();
    return new AnalyticsPercentageItemFilter(clonedFilters, cloneyAggregation);
  }
}
