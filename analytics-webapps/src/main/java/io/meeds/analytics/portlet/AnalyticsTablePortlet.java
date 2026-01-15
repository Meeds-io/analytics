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
package io.meeds.analytics.portlet;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.chart.TableColumnResult;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.AnalyticsPeriod;
import io.meeds.analytics.model.filter.AnalyticsPeriodType;
import io.meeds.analytics.model.filter.AnalyticsTableColumnAggregation;
import io.meeds.analytics.model.filter.AnalyticsTableColumnFilter;
import io.meeds.analytics.model.filter.AnalyticsTableFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AnalyticsTablePortlet extends AbstractAnalyticsPortlet<AnalyticsTableFilter> {

  @Override
  protected String getViewPagePath() {
    return "/WEB-INF/jsp/analytics-table.jsp";
  }

  @Override
  protected Class<AnalyticsTableFilter> getFilterClass() {
    return AnalyticsTableFilter.class;
  }

  @Override
  protected void readSettingsReadOnly(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter filter = getFilter(request);
    JSONObject jsonResponse = new JSONObject();
    addJSONParam(jsonResponse, "title", filter.getTitle());
    addJSONParam(jsonResponse, "pageSize", filter.getPageSize());
    addJSONParam(jsonResponse, "canEdit", canModifySettings(request));
    addJSONParam(jsonResponse, "scope", getSearchScope(request).name());
    response.setContentType("application/json");
    response.getWriter().write(jsonResponse.toString());
  }

  @Override
  protected void readSettings(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter filter = getFilter(request);
    response.setContentType("application/json");
    response.getWriter().write(AnalyticsUtils.toJsonString(filter));
  }

  @Override
  protected void readData(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter tableFilter = getFilter(request);
    if (tableFilter.getMainColumn() == null
        || tableFilter.getMainColumn().getValueAggregation() == null
        || tableFilter.getMainColumn().getValueAggregation().getAggregation() == null
        || tableFilter.getMainColumn().getValueAggregation().getAggregation().getField() == null) {
      response.setContentType(MediaType.APPLICATION_JSON);
      response.getWriter().write("{}");
      return;
    }
    addTimeZoneFilter(request, tableFilter);

    String column = request.getParameter("column");
    int columnIndex = 0;
    if (StringUtils.isNotBlank(column)) {
      columnIndex = Integer.parseInt(column);
    }
    String fromDateString = request.getParameter("min");
    String toDateString = request.getParameter("max");
    AnalyticsPeriod period = new AnalyticsPeriod(Long.parseLong(fromDateString), Long.parseLong(toDateString));
    AnalyticsPeriodType periodType = null;
    String analyticsPeriodType = request.getParameter("periodType");
    if (StringUtils.isNotBlank(analyticsPeriodType)) {
      periodType = AnalyticsPeriodType.periodTypeByName(analyticsPeriodType);
      period = tableFilter.getCurrentPeriod(period, periodType);
    }
    AnalyticsFieldFilter fieldFilter = null;

    String fieldFilterName = request.getParameter("fieldFilter");
    String fieldFilterValues = request.getParameter("fieldValues");
    if (StringUtils.isNotBlank(fieldFilterName) && StringUtils.isNotBlank(fieldFilterValues)) {
      fieldFilter = new AnalyticsFieldFilter(fieldFilterName, AnalyticsFieldFilterType.IN_SET, fieldFilterValues);
    }

    int limit;
    try {
      limit = Integer.parseInt(request.getParameter("limit"));
    } catch (NumberFormatException e) {
      limit = 0;
    }
    String sort = request.getParameter("sort");

    AnalyticsFilter filter = tableFilter.buildColumnFilter(period,
                                                           periodType,
                                                           fieldFilter,
                                                           limit,
                                                           sort,
                                                           columnIndex,
                                                           true);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);

    TableColumnResult result = getAnalyticsService().computeTableColumnData(null,
                                                                            tableFilter,
                                                                            filter,
                                                                            period,
                                                                            periodType,
                                                                            columnIndex,
                                                                            true);
    AnalyticsTableColumnFilter columnFilter = tableFilter.getColumnFilter(columnIndex);
    if (columnFilter.getThresholdAggregation() != null
        && columnFilter.getThresholdAggregation().getAggregation() != null
        && columnFilter.getThresholdAggregation().getAggregation().getType() != null
        && columnFilter.getThresholdAggregation().getAggregation().getField() != null) {
      filter = tableFilter.buildColumnFilter(period,
                                             periodType,
                                             fieldFilter,
                                             limit,
                                             sort,
                                             columnIndex,
                                             false);
      addScopeFilter(request, filter);
      addLanguageFilter(request, filter);

      getAnalyticsService().computeTableColumnData(result,
                                                   tableFilter,
                                                   filter,
                                                   period,
                                                   periodType,
                                                   columnIndex,
                                                   false);
    }

    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(result));
  }

  private AnalyticsTableFilter getFilter(ResourceRequest request) {
    AnalyticsTableFilter filter = getFilterFromPreferences(request);
    Set<StatisticFieldMapping> mappings = getAnalyticsService().retrieveMapping(false);
    Set<String> fieldNames = mappings.stream()
                                     .map(StatisticFieldMapping::getName)
                                     .collect(Collectors.toSet());
    List<AnalyticsTableColumnFilter> columns = filter.getColumns();
    for (AnalyticsTableColumnFilter analyticsTableColumnFilter : columns) {
      convertToAltFieldName(analyticsTableColumnFilter, fieldNames);
    }
    convertToAltFieldName(filter.getMainColumn(), fieldNames);
    return filter;
  }

  private void convertToAltFieldName(AnalyticsTableColumnFilter columnFilter, Set<String> fieldNames) {
    if (columnFilter != null) {
      convertToAltFieldName(columnFilter::getUserField,
                            columnFilter::setUserField,
                            fieldNames);
      convertToAltFieldName(columnFilter::getSpaceField,
                            columnFilter::setSpaceField,
                            fieldNames);
      convertToAltFieldName(columnFilter.getThresholdAggregation(),
                            fieldNames);
      convertToAltFieldName(columnFilter.getValueAggregation(),
                            fieldNames);
    }
  }

  private void convertToAltFieldName(AnalyticsTableColumnAggregation columnAggregation, Set<String> fieldNames) {
    if (columnAggregation != null) {
      AnalyticsAggregation aggregation = columnAggregation.getAggregation();
      if (aggregation != null) {
        convertToAltFieldName(aggregation::getField,
                              aggregation::setField,
                              fieldNames);
      }
      List<AnalyticsFieldFilter> filters = columnAggregation.getFilters();
      if (CollectionUtils.isNotEmpty(filters)) {
        for (AnalyticsFieldFilter analyticsFilter : filters) {
          convertToAltFieldName(analyticsFilter::getField,
                                analyticsFilter::setField,
                                fieldNames);
        }
      }
    }
  }

}
