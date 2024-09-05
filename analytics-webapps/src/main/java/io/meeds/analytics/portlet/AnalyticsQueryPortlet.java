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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.apache.commons.lang3.StringUtils;

import com.google.javascript.jscomp.jarjar.com.google.re2j.Pattern;

import org.exoplatform.commons.api.portlet.GenericDispatchedViewPortlet;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.social.webui.Utils;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AnalyticsQueryPortlet extends GenericDispatchedViewPortlet {

  private static final String        FROM_TIMESTAMP_PARAM = "{fromTimestamp}";

  private static final String        TO_TIMESTAMP_PARAM   = "{toTimestamp}";

  private static final String        SPACE_IDS_PARAM      = "{spaceIds}";

  private static final Pattern       NUMBER_LIST_PATTERN  = Pattern.compile("[\\d,]*");

  private static Map<String, String> filters              = new ConcurrentHashMap<>();

  private AnalyticsService           analyticsService;

  @Override
  public final void serveResource(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    AnalyticsFilter filter;
    try {
      filter = getFilter(request);
    } catch (IllegalAccessException e) {
      throw new PortletException(e);
    }

    String limit = request.getParameter("limit");
    if (StringUtils.isBlank(limit)) {
      limit = request.getParameter("xLimit");
      filter.getXAxisAggregations().get(0).setLimit(Long.parseLong(limit));
    } else {
      filter.setLimit(Long.parseLong(limit));
    }

    ChartDataList result = getAnalyticsService().computeChartData(filter);
    response.getWriter().write(AnalyticsUtils.toJsonString(result));
    response.setContentType("application/json");
  }

  private AnalyticsFilter getFilter(ResourceRequest request) throws IllegalAccessException {
    String filterName = request.getParameter("queryName");
    String filterString = getFilterContent(filterName);
    if (filterString.contains("{userIdentityId}")) {
      filterString = filterString.replace("{userIdentityId}", Utils.getViewerIdentityId());
    }
    if (filterString.contains(SPACE_IDS_PARAM)) {
      String spaceIds = request.getParameter("spaceIds");
      if (!NUMBER_LIST_PATTERN.matches(spaceIds)) {
        throw new IllegalAccessException("Illegal Chars found in parameter 'spaceIds'");
      }
      filterString = filterString.replace(SPACE_IDS_PARAM, spaceIds);
    }
    if (filterString.contains(FROM_TIMESTAMP_PARAM)) {
      String spaceIds = request.getParameter("fromTimestamp");
      if (!NUMBER_LIST_PATTERN.matches(spaceIds)) {
        throw new IllegalAccessException("Illegal Chars found in parameter 'fromTimestamp'");
      }
      filterString = filterString.replace(FROM_TIMESTAMP_PARAM, spaceIds);
    }
    if (filterString.contains(TO_TIMESTAMP_PARAM)) {
      String spaceIds = request.getParameter("toTimestamp");
      if (!NUMBER_LIST_PATTERN.matches(spaceIds)) {
        throw new IllegalAccessException("Illegal Chars found in parameter 'toTimestamp'");
      }
      filterString = filterString.replace(TO_TIMESTAMP_PARAM, spaceIds);
    }
    return AnalyticsUtils.fromJsonString(filterString, AnalyticsFilter.class);
  }

  private String getFilterContent(String filterName) {
    return filters.computeIfAbsent(filterName, n -> getInitParameter(filterName));
  }

  private AnalyticsService getAnalyticsService() {
    if (analyticsService == null) {
      analyticsService = CommonsUtils.getService(AnalyticsService.class);
    }
    return analyticsService;
  }

}
