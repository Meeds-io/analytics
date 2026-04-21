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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.javascript.jscomp.jarjar.com.google.re2j.Pattern;

import org.exoplatform.commons.api.portlet.GenericDispatchedViewPortlet;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.webui.Utils;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.utils.AnalyticsUtils;

import static io.meeds.analytics.utils.AnalyticsUtils.convertToAltFieldName;

public class AnalyticsQueryPortlet extends GenericDispatchedViewPortlet {

  private static final String        FROM_TIMESTAMP_PARAM   = "{fromTimestamp}";

  private static final String        TO_TIMESTAMP_PARAM     = "{toTimestamp}";

  private static final String        SPACE_IDS_PARAM        = "{spaceIds}";

  private static final String        SPACE_MEMBER_IDS_PARAM = "{spaceMemberIds}";

  private static final Pattern       NUMBER_LIST_PATTERN    = Pattern.compile("[\\d,]*");

  private static final int           MAX_SPACE_IDS          = 1000;

  private static final String        PARENT_SPACE_ID        = "parentSpaceId";

  private static Map<String, String> filters                = new ConcurrentHashMap<>();

  private AnalyticsService           analyticsService;

  private SpaceService               spaceService;

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

  private AnalyticsFilter getFilterFromRequest(ResourceRequest request) throws IllegalAccessException {
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
    if (filterString.contains(SPACE_MEMBER_IDS_PARAM)) {
      List<String> memberSpacesIds = getSpaceService().getMemberSpacesIds(request.getRemoteUser(), 0, MAX_SPACE_IDS);
      if (memberSpacesIds.isEmpty()) {
        memberSpacesIds.add("-1");
      }
      filterString = filterString.replace(SPACE_MEMBER_IDS_PARAM, StringUtils.join(memberSpacesIds, ","));
    }
    if (filterString.contains(FROM_TIMESTAMP_PARAM)) {
      String fromTimestamp = request.getParameter("fromTimestamp");
      if (!NUMBER_LIST_PATTERN.matches(fromTimestamp)) {
        throw new IllegalAccessException("Illegal Chars found in parameter 'fromTimestamp'");
      }
      filterString = filterString.replace(FROM_TIMESTAMP_PARAM, fromTimestamp);
    }
    if (filterString.contains(TO_TIMESTAMP_PARAM)) {
      String toTimestamp = request.getParameter("toTimestamp");
      if (!NUMBER_LIST_PATTERN.matches(toTimestamp)) {
        throw new IllegalAccessException("Illegal Chars found in parameter 'toTimestamp'");
      }
      filterString = filterString.replace(TO_TIMESTAMP_PARAM, toTimestamp);
    }
    return AnalyticsUtils.fromJsonString(filterString, AnalyticsFilter.class);
  }

  private AnalyticsFilter getFilter(ResourceRequest request) throws IllegalAccessException {
    AnalyticsFilter filter = getFilterFromRequest(request);
    applyParentSpaceFilter(request, filter);
    Set<StatisticFieldMapping> mappings = getAnalyticsService().retrieveMapping(false);
    Set<String> fieldNames = mappings.stream().map(StatisticFieldMapping::getName).collect(Collectors.toSet());
    if (CollectionUtils.isNotEmpty(filter.getAggregations())) {
      for (AnalyticsAggregation analyticsAggregation : filter.getAggregations()) {
        convertToAltFieldName(analyticsAggregation::getField,
                              analyticsAggregation::setField,
                              fieldNames);
      }
    }
    if (CollectionUtils.isNotEmpty(filter.getFilters())) {
      for (AnalyticsFieldFilter analyticsFilter : filter.getFilters()) {
        convertToAltFieldName(analyticsFilter::getField,
                              analyticsFilter::setField,
                              fieldNames);
      }
    }
    return filter;
  }

  private String getFilterContent(String filterName) {
    return filters.computeIfAbsent(filterName, n -> getInitParameter(filterName));
  }

  private AnalyticsService getAnalyticsService() {
    if (analyticsService == null) {
      analyticsService = ExoContainerContext.getService(AnalyticsService.class);
    }
    return analyticsService;
  }

  private SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = ExoContainerContext.getService(SpaceService.class);
    }
    return spaceService;
  }

  private void applyParentSpaceFilter(ResourceRequest request, AnalyticsFilter filter) {
    String parentSpaceId = request.getParameter(PARENT_SPACE_ID);
    if (StringUtils.isBlank(parentSpaceId)) {
      return;
    }
    List<AnalyticsFieldFilter> fieldFilters = filter.getFilters();
    if (fieldFilters == null) {
      fieldFilters = new ArrayList<>();
    }
    fieldFilters.add(new AnalyticsFieldFilter(PARENT_SPACE_ID, AnalyticsFieldFilterType.EQUAL, parentSpaceId));
    filter.setFilters(fieldFilters);
  }

}
