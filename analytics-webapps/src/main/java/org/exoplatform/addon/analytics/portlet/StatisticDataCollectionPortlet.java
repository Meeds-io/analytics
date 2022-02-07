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
import java.util.List;

import javax.portlet.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.api.service.AnalyticsService;
import org.exoplatform.analytics.api.service.StatisticWatcher;
import org.exoplatform.analytics.api.websocket.AnalyticsWebSocketService;
import org.exoplatform.analytics.api.websocket.UserSettings;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.container.ExoContainerContext;

public class StatisticDataCollectionPortlet extends GenericPortlet {

  private static final String       JSP_FILE = "/WEB-INF/jsp/statistics-collection.jsp";

  private AnalyticsWebSocketService analyticsWebSocketService;

  private AnalyticsService          analyticsService;

  @Override
  public void init() throws PortletException {
    super.init();
    getAnalyticsWebSocketService().init();
  }

  @Override
  protected void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
    if (StringUtils.isNotBlank(request.getRemoteUser())) {
      UserSettings userSettings = getAnalyticsWebSocketService().getUserSettings(request.getRemoteUser());
      request.setAttribute("userSettings", AnalyticsUtils.toJsonString(userSettings));

      List<StatisticWatcher> uiWatchers = getAnalyticsService().getUIWatchers();
      request.setAttribute("uiWatchers", AnalyticsUtils.toJsonString(uiWatchers));

      PortletRequestDispatcher dispatcher = getPortletContext().getRequestDispatcher(JSP_FILE);
      dispatcher.include(request, response);
    }
  }

  public AnalyticsService getAnalyticsService() {
    if (analyticsService == null) {
      analyticsService = ExoContainerContext.getService(AnalyticsService.class);
    }
    return analyticsService;
  }

  public AnalyticsWebSocketService getAnalyticsWebSocketService() {
    if (analyticsWebSocketService == null) {
      analyticsWebSocketService = ExoContainerContext.getService(AnalyticsWebSocketService.class);
    }
    return analyticsWebSocketService;
  }
}
