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
package io.meeds.analytics.model.chart;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

import lombok.*;
import lombok.EqualsAndHashCode.Exclude;

@AllArgsConstructor
@EqualsAndHashCode
public class ChartAggregationResult implements Serializable {

  private static final long     serialVersionUID = 4036864369153698277L;

  @Exclude
  private ChartAggregationLabel chartLabel;

  private String                key;

  @Exclude
  @Getter
  private String                result;

  public String getLabel() {
    return chartLabel.getLabel();
  }

  public String getValue() {
    return StringUtils.isBlank(result) || StringUtils.equals(result, "null") ? "0" : result;
  }

  public ChartAggregationLabel retrieveChartLabel() {
    return chartLabel;
  }

}
