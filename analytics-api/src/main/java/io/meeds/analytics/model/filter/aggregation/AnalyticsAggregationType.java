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
package io.meeds.analytics.model.filter.aggregation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AnalyticsAggregationType {

  SUM("sum", true, false, false, false),
  AVG("avg", true, false, false, false),
  MAX("max", true, false, false, false),
  MIN("min", true, false, false, false),
  COUNT("value_count", true, false, false, false),
  CARDINALITY("cardinality", true, false, false, false),
  TERMS("terms", false, false, true, true),
  DATE("date_histogram", false, true, false, false),
  HISTOGRAM("histogram", false, true, false, false);

  @Getter
  private String  aggName;

  @Getter
  private boolean numericResult;

  @Getter
  private boolean useInterval;

  @Getter
  private boolean useSort;

  @Getter
  private boolean useLimit;
}
