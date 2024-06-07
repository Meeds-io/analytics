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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTableColumnAggregation implements Serializable, Cloneable {

  private static final long          serialVersionUID = -7765464084735381851L;

  private AnalyticsAggregation       aggregation;

  private List<AnalyticsFieldFilter> filters          = new ArrayList<>();

  private boolean                    periodIndependent;

  @Override
  public AnalyticsTableColumnAggregation clone() { // NOSONAR
    AnalyticsAggregation clonedAggregation = aggregation == null ? null : aggregation.clone();
    List<AnalyticsFieldFilter> clonedFilters = filters.stream()
                                                      .map(AnalyticsFieldFilter::clone)
                                                      .toList();
    return new AnalyticsTableColumnAggregation(clonedAggregation, clonedFilters, periodIndependent);
  }
}
