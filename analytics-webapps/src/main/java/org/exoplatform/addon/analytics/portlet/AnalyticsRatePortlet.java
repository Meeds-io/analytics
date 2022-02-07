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
import java.time.Instant;
import java.time.LocalDate;

import javax.portlet.*;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import org.exoplatform.analytics.model.filter.*;
import org.exoplatform.analytics.utils.AnalyticsUtils;

public class AnalyticsRatePortlet extends AbstractAnalyticsPortlet<AnalyticsPercentageFilter> {

  protected String getViewPagePath() {
    return "/WEB-INF/jsp/analytics-rate.jsp";
  }

  @Override
  protected Class<AnalyticsPercentageFilter> getFilterClass() {
    return AnalyticsPercentageFilter.class;
  }

  @Override
  protected void readSettingsReadOnly(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsPercentageFilter filter = getFilterFromPreferences(request);
    JSONObject jsonResponse = new JSONObject();
    addJSONParam(jsonResponse, "title", filter.getTitle());
    addJSONParam(jsonResponse, "chartType", filter.getChartType());
    addJSONParam(jsonResponse, "colors", filter.getColors());
    addJSONParam(jsonResponse, "canEdit", canModifySettings(request));
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(jsonResponse.toString());
  }

  @Override
  protected void readSettings(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsPercentageFilter filter = getFilterFromPreferences(request);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(filter));
  }

  @Override
  protected void readData(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsPercentageFilter filter = getFilterFromPreferences(request);
    addLanguageFilter(request, filter);
    addTimeZoneFilter(request, filter);
    addPeriodFilter(request, filter);
    Object result = getAnalyticsService().computePercentageChartData(filter);
    response.setContentType(MediaType.APPLICATION_JSON);
    response.getWriter().write(AnalyticsUtils.toJsonString(result));
  }

  private void addLanguageFilter(ResourceRequest request, AnalyticsPercentageFilter filter) {
    String lang = request.getParameter("lang");
    filter.setLang(lang);
  }

  private void addPeriodFilter(ResourceRequest request, AnalyticsPercentageFilter filter) {
    String fromDateString = request.getParameter("min");
    String toDateString = request.getParameter("max");
    String periodType = request.getParameter("periodType");

    AnalyticsPeriod period = new AnalyticsPeriod(Long.parseLong(fromDateString), Long.parseLong(toDateString), filter.zoneId());
    if (StringUtils.isNotBlank(periodType)) {
      AnalyticsPeriodType analyticsPeriodType = AnalyticsPeriodType.periodTypeByName(periodType);
      long middleOfPeriod = (period.getFromInMS() + period.getToInMS()) / 2;
      LocalDate middlePeriodDate = Instant.ofEpochMilli(middleOfPeriod).atZone(filter.zoneId()).toLocalDate();
      period = analyticsPeriodType.getCurrentPeriod(middlePeriodDate, filter.zoneId());
      middleOfPeriod = (period.getFromInMS() + period.getToInMS()) / 2;
      filter.setPeriodDateInMS(middleOfPeriod);
      filter.setPeriodType(periodType);
    } else {
      filter.setCustomPeriod(period);
    }
  }
}
