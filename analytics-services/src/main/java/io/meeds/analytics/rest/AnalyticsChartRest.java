/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.analytics.rest;

import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.chart.ChartAggregationResult;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("chart")
@Tag(name = "/analytics/rest/chart", description = "Retrieve Analytics Chart Data")
public class AnalyticsChartRest {

  @Autowired
  private AnalyticsService analyticsService;

  @GetMapping
  @Secured("analytics")
  @Operation(summary = "Retrieve Analytics two dimensions Chat Data",
             method = "GET",
             description = "This will compute a two dimensions matrix of data useful for chart display")
  @ApiResponses(value = {
                          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
  })
  public List<ChartAggregationResult> getChart(
                                               @Parameter(description = "Collected Statistic Operations to consider in query",
                                                          required = false)
                                               @RequestParam(name = "operation",
                                                             required = false)
                                               List<String> operations,
                                               @Parameter(description = "Filtered field name",
                                                          required = false)
                                               @RequestParam(name = "fieldName",
                                                             required = false)
                                               String fieldName,
                                               @Parameter(description = "Filtered field values",
                                                          required = false)
                                               @RequestParam(name = "fieldValue",
                                                             required = false)
                                               List<String> fieldValues,
                                               @Parameter(description = "X Axis aggregation field",
                                                          required = true)
                                               @RequestParam("xAggregationField")
                                               String xAggregationField,
                                               @Parameter(description = "X Axis aggregation type: MAX, MIN, SUM, COUNT, CARDINALITY (COUNT Distinct), TERMS, DATA or HISTOGRAM",
                                                          required = true)
                                               @RequestParam("xAggregationType")
                                               AnalyticsAggregationType xAggregationType,
                                               @Parameter(description = "X Axis sort direction: asc or desc",
                                                          required = false)
                                               @RequestParam(name = "xAggregationSortDirection",
                                                             required = false)
                                               String xAggregationSortDirection,
                                               @Parameter(description = "Y Axis aggregation field",
                                                          required = true)
                                               @RequestParam("yAggregationField")
                                               String yAggregationField,
                                               @Parameter(description = "Y Axis aggregation type: MAX, MIN, SUM, COUNT, CARDINALITY (COUNT Distinct), TERMS, DATA or HISTOGRAM",
                                                          required = true)
                                               @RequestParam("yAggregationType")
                                               AnalyticsAggregationType yAggregationType,
                                               @Parameter(description = "Y Axis sort direction: asc or desc",
                                                          required = true)
                                               @RequestParam(name = "yAggregationSortDirection",
                                                             required = false)
                                               String yAggregationSortDirection,
                                               @Parameter(description = "Limit of x Axis results to retrieve",
                                                          required = true)
                                               @RequestParam("limit")
                                               int limit) {
    ChartDataList chartData = analyticsService.getChart(operations,
                                                        fieldName,
                                                        fieldValues,
                                                        xAggregationField,
                                                        xAggregationType,
                                                        xAggregationSortDirection,
                                                        yAggregationField,
                                                        yAggregationType,
                                                        yAggregationSortDirection,
                                                        limit);
    if (chartData != null && CollectionUtils.size(chartData.getCharts()) == 1) {
      return chartData.getCharts().getFirst().getAggregationResults();
    } else {
      return Collections.emptyList();
    }
  }

}
