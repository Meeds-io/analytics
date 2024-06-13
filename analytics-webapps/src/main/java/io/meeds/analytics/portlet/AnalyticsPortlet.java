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
import java.util.Collections;
import java.util.List;

import javax.portlet.*;
import javax.ws.rs.core.MediaType;

import org.json.JSONArray;
import org.json.JSONObject;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AnalyticsPortlet extends AbstractAnalyticsPortlet<AnalyticsFilter> {

  @Override
  protected String getViewPagePath() {
    return "/WEB-INF/jsp/analytics.jsp";
  }

  @Override
  protected Class<AnalyticsFilter> getFilterClass() {
    return AnalyticsFilter.class;
  }

  @Override
  protected void readSettings(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilterFromPreferences(request);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(filter));
  }

  @Override
  protected void readSettingsReadOnly(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilterFromPreferences(request);
    JSONObject jsonResponse = new JSONObject();
    addJSONParam(jsonResponse, "title", filter.getTitle());
    addJSONParam(jsonResponse, "chartType", filter.getChartType());
    List<String> colors = filter.getColors() == null ? Collections.emptyList() : filter.getColors();
    addJSONParam(jsonResponse, "colors", new JSONArray(colors));
    addJSONParam(jsonResponse, "canEdit", canModifySettings(request));
    addJSONParam(jsonResponse, "scope", getSearchScope(request).name());
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(jsonResponse.toString());
  }

  @Override
  protected void readSamples(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilterFromPreferences(request);
    addPeriodFilter(request, filter);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);
    addLimitFilter(request, filter);
    addTimeZoneFilter(request, filter);
    addSortFilter(filter, "desc");

    List<StatisticData> statisticDatas = getAnalyticsService().retrieveData(filter);
    List<JSONObject> objectMappings = statisticDatas.stream()
                                                    .map(statisticData -> {
                                                      JSONObject object = new JSONObject(statisticData);
                                                      object.remove("class");
                                                      return object;
                                                    })
                                                    .toList();
    JSONArray jsonArrayResponse = new JSONArray(objectMappings);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(jsonArrayResponse.toString());
  }

  @Override
  protected void readData(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter = getFilterFromPreferences(request);
    addPeriodFilter(request, filter);
    addScopeFilter(request, filter);
    addLanguageFilter(request, filter);
    addTimeZoneFilter(request, filter);

    Object result = getAnalyticsService().computeChartData(filter);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(result));
  }

}
