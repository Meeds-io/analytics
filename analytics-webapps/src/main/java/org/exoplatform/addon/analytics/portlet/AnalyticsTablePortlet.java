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
package org.exoplatform.addon.analytics.portlet;

import java.io.IOException;

import javax.portlet.*;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import org.exoplatform.analytics.model.chart.TableColumnResult;
import org.exoplatform.analytics.model.filter.*;
import org.exoplatform.analytics.model.filter.search.AnalyticsFieldFilter;
import org.exoplatform.analytics.model.filter.search.AnalyticsFieldFilterType;
import org.exoplatform.analytics.utils.AnalyticsUtils;

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
    AnalyticsTableFilter filter = getFilterFromPreferences(request);
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
    AnalyticsTableFilter filter = getFilterFromPreferences(request);
    response.setContentType("application/json");
    response.getWriter().write(AnalyticsUtils.toJsonString(filter));
  }

  @Override
  protected void readData(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsTableFilter tableFilter = getFilterFromPreferences(request);
    if (tableFilter == null || tableFilter.getMainColumn() == null
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
}
