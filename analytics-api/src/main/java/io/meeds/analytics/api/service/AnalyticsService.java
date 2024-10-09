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
package io.meeds.analytics.api.service;

import java.util.List;
import java.util.Set;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.StatisticFieldValue;
import io.meeds.analytics.model.StatisticWatcher;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.chart.PercentageChartResult;
import io.meeds.analytics.model.chart.PercentageChartValue;
import io.meeds.analytics.model.chart.TableColumnResult;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.AnalyticsPercentageFilter;
import io.meeds.analytics.model.filter.AnalyticsPeriod;
import io.meeds.analytics.model.filter.AnalyticsPeriodType;
import io.meeds.analytics.model.filter.AnalyticsTableFilter;

public interface AnalyticsService {

  /**
   * Retrieve analytics chart data
   * 
   * @param filter used search filters and aggregations to compute data
   * @return computed analytics chart data
   */
  ChartDataList computeChartData(AnalyticsFilter filter);

  /**
   * Retrieve analytics percentage chart data
   * 
   * @param filter used search filters and aggregations to compute data
   * @return computed analytics chart data
   */
  PercentageChartResult computePercentageChartData(AnalyticsPercentageFilter filter);

  /**
   * Retrieve analytics table data
   * 
   * @param columnResult existing column result to use for result comptuing
   * @param tableFilter overall configured table filter
   * @param filter used search filters and aggregations to compute data
   * @param period selected {@link AnalyticsPeriod}
   * @param periodType selected {@link AnalyticsPeriodType}
   * @param columnIndex column index in the configured table filter
   * @param isValue whether the filter to compute is for value or threshold
   * @return computed analytics table column data
   */
  TableColumnResult computeTableColumnData(TableColumnResult columnResult,
                                           AnalyticsTableFilter tableFilter,
                                           AnalyticsFilter filter,
                                           AnalyticsPeriod period,
                                           AnalyticsPeriodType periodType,
                                           int columnIndex,
                                           boolean isValue);

  /**
   * Retrieve analytics percentage chart data
   * 
   * @param filter used search filters and aggregations to compute data
   * @param currentPeriod current period of time
   * @param previousPeriod previous period of time to compare with
   * @param hasLimitAggregation whether aggregations has limit aggregation or
   *          not
   * @return {@link PercentageChartValue} containing current and previous values
   *         and thresholds
   */
  PercentageChartValue computePercentageChartData(AnalyticsFilter filter,
                                                  AnalyticsPeriod currentPeriod,
                                                  AnalyticsPeriod previousPeriod,
                                                  boolean hasLimitAggregation);

  /**
   * Retrieve data using search filters
   * 
   * @param searchFilter
   * @return {@link List} of {@link StatisticData}
   */
  List<StatisticData> retrieveData(AnalyticsFilter searchFilter);

  /**
   * @param forceRefresh whether force refresh from ES or not
   * @return a {@link Set} of ES mapping fields
   */
  Set<StatisticFieldMapping> retrieveMapping(boolean forceRefresh);

  /**
   * Retrieve values of a selected field
   * 
   * @param field name of esField
   * @param limit limit of results to retrieve
   * @return {@link List} of field values of type {@link StatisticFieldValue}
   */
  List<StatisticFieldValue> retrieveFieldValues(String field, int limit);

  /**
   * @return the analytics permission expression
   */
  List<String> getAdministratorsPermissions();

  /**
   * @return the {@link List} of permission expression of users that can access
   *         all analytics datas
   */
  List<String> getViewAllPermissions();

  /**
   * @return the {@link List} of permission expression of users that can access
   *         their (and their spaces they are member of) analytics datas
   */
  List<String> getViewPermissions();

  /**
   * @return {@link List} of {@link StatisticWatcher} containing DOM selectors
   *         of elements to watch
   */
  List<StatisticWatcher> getUIWatchers();

  /**
   * @param name corresponding UI watcher name, see StatisticWatcher#getName()
   * @return {@link StatisticWatcher} having switch corresponding name
   */
  StatisticWatcher getUIWatcher(String name);

  /**
   * @param uiWatcher {@link StatisticWatcher} containing DOM selector of
   *          elements to watch
   */
  void addUIWatcher(StatisticWatcher uiWatcher);

}
