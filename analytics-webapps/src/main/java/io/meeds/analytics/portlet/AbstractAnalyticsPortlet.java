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
import java.util.*;

import javax.portlet.*;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.json.*;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.*;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.webui.Utils;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.StatisticFieldValue;
import io.meeds.analytics.model.filter.AbstractAnalyticsFilter;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.utils.AnalyticsUtils;

public abstract class AbstractAnalyticsPortlet<T> extends GenericPortlet {

  private static final Log    LOG                         = ExoLogger.getLogger(AbstractAnalyticsPortlet.class);

  private static final String CAN_MODIFY_CHART_SETTINGS   = "canModifyChartSettings";

  private static final String ANALYTICS_SEARCH_SCOPE      = "analyticsSearchScope";

  private static final String READ_SETTINGS_OPERATION     = "GET_SETTINGS";

  private static final String READ_FILTERS_OPERATION      = "GET_FILTERS";

  private static final String READ_MAPPINGS_OPERATION     = "GET_MAPPINGS";

  private static final String READ_DATA_OPERATION         = "GET_DATA";

  private static final String READ_SAMPLES_OPERATION      = "GET_SAMPLES";

  private static final String READ_FIELD_VALUES_OPERATION = "GET_FIELD_VALUES";

  private SpaceService        spaceService;

  private AnalyticsService    analyticsService;

  @Override
  public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
    PortletRequestDispatcher dispatcher = getPortletContext().getRequestDispatcher(getViewPagePath());
    dispatcher.forward(request, response);
  }

  @Override
  public final void serveResource(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
    String operation = request.getParameter("operation");

    if (StringUtils.equals(operation, READ_SETTINGS_OPERATION)) {
      readSettingsReadOnly(request, response);
    } else if (StringUtils.equals(operation, READ_FILTERS_OPERATION)) {
      readSettings(request, response);
    } else if (StringUtils.equals(operation, READ_MAPPINGS_OPERATION)) {
      Set<StatisticFieldMapping> mappings = getAnalyticsService().retrieveMapping(false);
      List<JSONObject> objectMappings = mappings.stream().map(JSONObject::new).toList();
      JSONArray jsonArrayResponse = new JSONArray(objectMappings);
      response.setContentType(MediaType.APPLICATION_JSON);
      response.getWriter().write(jsonArrayResponse.toString());
    } else if (StringUtils.equals(operation, READ_SAMPLES_OPERATION)) {
      if (!canModifySettings(request)) {
        throw new PortletException("Not allowed to access samples");
      }
      readSamples(request, response);
    } else if (StringUtils.equals(operation, READ_DATA_OPERATION)) {
      readData(request, response);
    } else if (StringUtils.equals(operation, READ_FIELD_VALUES_OPERATION)) {
      String field = request.getParameter("field");
      String limitString = request.getParameter("limit");
      int limit = StringUtils.isBlank(limitString) ? 10 : Integer.parseInt(limitString);
      if (StringUtils.isNotBlank(field)) {
        List<StatisticFieldValue> fieldValues = getAnalyticsService().retrieveFieldValues(field, limit);
        response.getWriter().write(AnalyticsUtils.toJsonString(fieldValues));
        response.setContentType("application/json");
      } else {
        response.getWriter().write("[]");
        response.setContentType("application/json");
      }
    }
    super.serveResource(request, response);
  }

  @Override
  public void processAction(ActionRequest request, ActionResponse response) throws IOException, PortletException {
    if (!canModifySettings(request)) {
      throw new PortletException("User is not allowed to save chart settings");
    }

    PortletPreferences preferences = request.getPreferences();
    Enumeration<String> parameterNames = request.getParameterNames();
    if (parameterNames != null && parameterNames.hasMoreElements()) {
      while (parameterNames.hasMoreElements()) {
        String name = parameterNames.nextElement();
        String paramValue = request.getParameter(name);
        preferences.setValue(name, paramValue);
      }
    }
    preferences.store();
    response.setPortletMode(PortletMode.VIEW);
  }

  protected void readSettingsReadOnly(ResourceRequest request,
                                      ResourceResponse response) throws PortletException, IOException {
    throw new UnsupportedOperationException();
  }

  protected abstract String getViewPagePath();

  protected abstract Class<T> getFilterClass();

  protected abstract void readSettings(ResourceRequest request,
                                       ResourceResponse response) throws PortletException,
                                                                  IOException;

  protected abstract void readData(ResourceRequest request,
                                   ResourceResponse response) throws PortletException, IOException;

  protected void readSamples(ResourceRequest request,
                             ResourceResponse response) throws PortletException, IOException {
    throw new UnsupportedOperationException();
  }

  protected T getFilterFromPreferences(ResourceRequest request) {
    T filter = null;
    PortletPreferences preferences = request == null ? null : request.getPreferences();
    if (preferences != null) {
      String analyticsFilterString = preferences.getValue("settings", null);
      if (StringUtils.isNotBlank(analyticsFilterString)) {
        filter = AnalyticsUtils.fromJsonString(analyticsFilterString, getFilterClass());
      }
    }
    try {
      if (filter == null) {
        filter = getFilterClass().getConstructor().newInstance();
      }
    } catch (Exception e) {
      LOG.debug("Error while instanciating object.", e);
    }
    return clone(filter);
  }

  protected SearchScope getSearchScope(PortletRequest request) { // NOSONAR
    PortletSession portletSession = request.getPortletSession();
    SearchScope searchScopeFromCache = getSearchScopeFromCache(portletSession);
    if (searchScopeFromCache != null) {
      return searchScopeFromCache;
    }
    ConversationState current = ConversationState.getCurrent();
    if (current == null || current.getIdentity() == null) {
      return cacheSearchScope(portletSession, SearchScope.NONE);
    }
    Identity identity = current.getIdentity();
    String userId = identity.getUserId();
    if (StringUtils.isBlank(userId) || StringUtils.equals(userId, IdentityConstants.ANONIM)
        || StringUtils.equals(userId, IdentityConstants.SYSTEM)) {
      return cacheSearchScope(portletSession, SearchScope.NONE);
    }

    List<String> groupIds = getAnalyticsService().getAdministratorsPermissions();
    for (String groupId : groupIds) {
      if (StringUtils.isNotBlank(groupId) && identity.isMemberOf(MembershipEntry.parse(groupId))) {
        return cacheSearchScope(portletSession, SearchScope.GLOBAL);
      }
    }

    groupIds = getAnalyticsService().getViewAllPermissions();
    for (String groupId : groupIds) {
      if (StringUtils.isNotBlank(groupId) && identity.isMemberOf(MembershipEntry.parse(groupId))) {
        return cacheSearchScope(portletSession, SearchScope.GLOBAL);
      }
    }

    groupIds = getAnalyticsService().getViewPermissions();
    boolean canView = false;
    for (String groupId : groupIds) {
      if (StringUtils.isNotBlank(groupId) && identity.isMemberOf(MembershipEntry.parse(groupId))) {
        canView = true;
        break;
      }
    }

    if (!canView) {
      return cacheSearchScope(portletSession, SearchScope.NONE);
    }

    Space space = SpaceUtils.getSpaceByContext();
    if (space != null) {
      if (getSpaceService().canViewSpace(space, userId)) {
        return cacheSearchScope(portletSession, SearchScope.SPACE);
      } else {
        return cacheSearchScope(portletSession, SearchScope.NONE);
      }
    }

    if (Utils.isOwner() || StringUtils.isBlank(Utils.getOwnerRemoteId())) {
      return cacheSearchScope(portletSession, SearchScope.USER);
    } else {
      return cacheSearchScope(portletSession, SearchScope.NONE);
    }
  }

  protected boolean canModifySettings(PortletRequest request) {
    PortletSession portletSession = request.getPortletSession();
    Boolean canModifyFromCache = getCanModifySettingsFromCache(portletSession);
    if (canModifyFromCache != null) {
      return canModifyFromCache;
    }
    ConversationState current = ConversationState.getCurrent();
    if (current == null || current.getIdentity() == null) {
      return cacheChartModificationAccessPermission(portletSession, false);
    }
    Identity identity = current.getIdentity();
    String userId = identity.getUserId();
    if (StringUtils.isBlank(userId) || StringUtils.equals(userId, IdentityConstants.ANONIM)
        || StringUtils.equals(userId, IdentityConstants.SYSTEM)) {
      return cacheChartModificationAccessPermission(portletSession, false);
    }
    List<String> groupIds = getAnalyticsService().getAdministratorsPermissions();
    for (String groupId : groupIds) {
      if (StringUtils.isNotBlank(groupId) && identity.isMemberOf(MembershipEntry.parse(groupId))) {
        return cacheChartModificationAccessPermission(portletSession, true);
      }
    }
    Space space = SpaceUtils.getSpaceByContext();
    if (space != null) {
      boolean canModify = getSpaceService().canManageSpace(space, userId);
      return cacheChartModificationAccessPermission(portletSession, canModify);
    }
    return cacheChartModificationAccessPermission(portletSession, false);
  }

  protected void addJSONParam(JSONObject jsonResponse, String paramName, Object paramValue) throws PortletException {
    try {
      jsonResponse.put(paramName, paramValue);
    } catch (JSONException e) {
      throw new PortletException("Error adding parameter to JSON", e);
    }
  }

  protected void addTimeZoneFilter(ResourceRequest request, AbstractAnalyticsFilter filter) {
    String timeZone = request.getParameter("timeZone");
    filter.setTimeZone(timeZone);
  }

  protected void addLanguageFilter(ResourceRequest request, AnalyticsFilter filter) {
    String lang = request.getParameter("lang");
    filter.setLang(lang);
  }

  protected void addSortFilter(AnalyticsFilter filter, String direction) {
    List<AnalyticsAggregation> xAxisAggregations = filter.getXAxisAggregations();
    for (AnalyticsAggregation analyticsAggregation : xAxisAggregations) {
      if (StringUtils.equals(AnalyticsUtils.FIELD_TIMESTAMP, analyticsAggregation.getField())) {
        analyticsAggregation.setSortDirection(direction);
      }
    }
  }

  protected void addLimitFilter(ResourceRequest request, AnalyticsFilter filter) {
    String limitString = request.getParameter("limit");
    if (StringUtils.isBlank(limitString)) {
      limitString = "10";
    }
    filter.setLimit(Long.parseLong(limitString));
  }

  protected void addPeriodFilter(ResourceRequest request, AnalyticsFilter filter) {
    String fromDateString = request.getParameter("min");
    String toDateString = request.getParameter("max");
    filter.addRangeFilter("timestamp", fromDateString, toDateString);
  }

  protected void addScopeFilter(ResourceRequest request, AnalyticsFilter filter) throws PortletException {
    SearchScope scope = getSearchScope(request);
    switch (scope) {
      case GLOBAL:
        break;
      case NONE:
        throw new PortletException("Not allowed to access information");
      case SPACE:
        Space space = SpaceUtils.getSpaceByContext();
        filter.addEqualFilter("spaceId", space.getId());
        break;
      case USER:
        String viewerIdentityId = Utils.getViewerIdentity().getId();
        filter.addEqualFilter("userId", viewerIdentityId);
        break;
    }
  }

  protected AnalyticsService getAnalyticsService() {
    if (analyticsService == null) {
      analyticsService = CommonsUtils.getService(AnalyticsService.class);
    }
    return analyticsService;
  }

  protected SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = CommonsUtils.getService(SpaceService.class);
    }
    return spaceService;
  }

  private Boolean getCanModifySettingsFromCache(PortletSession portletSession) {
    return (Boolean) portletSession.getAttribute(CAN_MODIFY_CHART_SETTINGS);
  }

  private boolean cacheChartModificationAccessPermission(PortletSession portletSession, boolean canModify) {
    if (!PropertyManager.isDevelopping()) {
      portletSession.setAttribute(CAN_MODIFY_CHART_SETTINGS, canModify);
    }
    return canModify;
  }

  private SearchScope getSearchScopeFromCache(PortletSession portletSession) {
    return (SearchScope) portletSession.getAttribute(ANALYTICS_SEARCH_SCOPE);
  }

  private SearchScope cacheSearchScope(PortletSession portletSession, SearchScope searchScope) {
    if (!PropertyManager.isDevelopping()) {
      portletSession.setAttribute(ANALYTICS_SEARCH_SCOPE, searchScope);
    }
    return searchScope;
  }

  @SuppressWarnings("unchecked")
  private T clone(T filter) {
    try {
      return filter == null ? null : (T) filter.getClass().getDeclaredMethod("clone").invoke(filter);
    } catch (Exception e) {
      LOG.debug("Error while cloning object. Returning original one.", e);
      return filter;
    }
  }

  enum SearchScope {
    USER,
    SPACE,
    GLOBAL,
    NONE
  }
}
