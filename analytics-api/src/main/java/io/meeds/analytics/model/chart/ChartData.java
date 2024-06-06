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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.*;

@Data
@ToString
@AllArgsConstructor
public class ChartData implements Serializable {

  private static final long            serialVersionUID   = 7951982952095482899L;

  private ChartAggregationValue        key;

  private List<ChartAggregationResult> aggregationResults = new ArrayList<>();

  private String                       lang;

  private String                       chartLabel;

  public String getChartKey() {
    return key == null || key.getAggregation() == null ? null
                                                       : key.getAggregation().getField();
  }

  public void addAggregationResult(ChartAggregationResult aggregationResult, int index, boolean replaceIfExists) {
    int existingIndex = aggregationResults.indexOf(aggregationResult);
    if (existingIndex < 0) {
      if (index < 0 || index >= aggregationResults.size()) {
        aggregationResults.add(aggregationResult);
      } else {
        aggregationResults.add(index, aggregationResult);
      }
    } else if (replaceIfExists) {
      aggregationResults.remove(existingIndex);
      if (index < 0 || index >= aggregationResults.size()) {
        aggregationResults.add(aggregationResult);
      } else {
        aggregationResults.add(index, aggregationResult);
      }
    }
  }

  public String getChartValue() {
    return key == null ? null : key.getFieldValue();
  }

  public List<String> getValues() {
    return aggregationResults.stream().map(ChartAggregationResult::getValue).collect(Collectors.toList());
  }

}
