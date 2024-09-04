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
import java.util.LinkedHashSet;
import java.util.List;

import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.utils.AnalyticsUtils;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ChartDataList implements Serializable {

  private static final long                    serialVersionUID  = 5490607865795348987L;

  private String                               lang;

  private LinkedHashSet<ChartAggregationLabel> aggregationLabels = new LinkedHashSet<>();

  private LinkedHashSet<ChartData>             charts            = new LinkedHashSet<>();

  private long                                 computingTime;

  private long                                 dataCount;

  public ChartDataList(String lang) {
    this.lang = lang;
  }

  public ChartData addAggregationResult(AnalyticsFilter filter,
                                        ChartAggregationValue chartParentAggregation,
                                        ChartAggregationResult aggregationResult) {
    ChartAggregationLabel chartLabel = aggregationResult.retrieveChartLabel();

    if (!aggregationLabels.contains(chartLabel)) {
      aggregationLabels.add(chartLabel);
    }

    ChartData chartData = charts.stream()
                                .filter(data -> (data.getKey() == null && chartParentAggregation == null)
                                                || (data.getKey() != null && data.getKey().equals(chartParentAggregation)))
                                .findFirst()
                                .orElse(null);
    if (chartData == null) {
      List<ChartAggregationResult> results = new ArrayList<>();

      chartData = new ChartData(chartParentAggregation, results, lang, null);

      String chartValue = chartData.getChartValue();
      String chartKey = chartData.getChartKey();
      String label = filter.isHideLabel() ? chartValue : AnalyticsUtils.compueLabel(chartKey, chartValue);

      chartData.setChartLabel(label);
      charts.add(chartData);
    }
    chartData.addAggregationResult(aggregationResult, -1, true);
    return chartData;
  }

  public List<String> getLabels() {
    return aggregationLabels.stream()
                            .map(result -> result.getLabel())
                            .toList();
  }

}
