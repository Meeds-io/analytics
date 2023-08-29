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
package org.exoplatform.analytics.listener.portal;

import static org.exoplatform.analytics.utils.AnalyticsUtils.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.model.StatisticData.StatisticStatus;
import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.mop.user.UserNode;
import org.exoplatform.portal.webui.portal.UIPortal;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.web.application.*;
import org.exoplatform.webui.application.WebuiRequestContext;

public class PageAccessListener extends BaseComponentPlugin implements ApplicationLifecycle<WebuiRequestContext> {

  private static final Log  LOG                = ExoLogger.getLogger(PageAccessListener.class);

  private ThreadLocal<Long> operationStartTime = new ThreadLocal<>();

  private boolean           collectAjaxQueries = false;

  public PageAccessListener(InitParams params) {
    if (params != null && params.containsKey("collectAjaxQueries")) {
      this.collectAjaxQueries = Boolean.parseBoolean(params.getValueParam("collectAjaxQueries").getValue());
    }
  }

  @Override
  public void onInit(Application app) throws Exception {
    // Not used
  }

  @Override
  public void onStartRequest(Application app, WebuiRequestContext context) throws Exception {
    operationStartTime.set(System.currentTimeMillis());
  }

  @Override
  public void onEndRequest(Application app, WebuiRequestContext context) throws Exception {
    StatisticData statisticData = buildStatisticData(context);
    if (statisticData == null) {
      operationStartTime.remove();
    } else {
      addStatisticData(statisticData);
    }
  }

  @Override
  public void onFailRequest(Application app, WebuiRequestContext context, RequestFailure failureType) {
    // Not used
  }

  @Override
  public void onDestroy(Application app) throws Exception {
    // Not used
  }

  private StatisticData buildStatisticData(WebuiRequestContext context) {
    try {
      PortalRequestContext portalRequestContext = (PortalRequestContext) context;
      if (portalRequestContext == null) {
        return null;
      }

      HttpServletRequest httpRequest = portalRequestContext.getRequest();
      boolean ajaxQuery = portalRequestContext.useAjax() || (httpRequest.getParameter("portal:componentId") != null);
      if (ajaxQuery && !collectAjaxQueries) {
        return null;
      }

      StatisticData statisticData = new StatisticData();
      statisticData.setModule("portal");
      if (ajaxQuery) {
        statisticData.setSubModule("webui");
        statisticData.setOperation("ajaxRequest");
      } else {
        statisticData.setSubModule("page");
        statisticData.setOperation("pageDisplay");
      }
      long userIdentityId = getUserIdentityId(context.getRemoteUser());
      statisticData.setUserId(userIdentityId);

      Space space = SpaceUtils.getSpaceByContext();
      addSpaceStatistics(statisticData, space);

      statisticData.addParameter("httpRequestMethod", httpRequest.getMethod());
      statisticData.addParameter("httpRequestUri", httpRequest.getRequestURI());
      statisticData.addParameter("httpRequestProtocol", httpRequest.getProtocol());
      statisticData.addParameter("httpRequestContentType", httpRequest.getContentType());
      statisticData.addParameter("httpRequestContentLength", httpRequest.getContentLength());

      statisticData.addParameter("userLocale", portalRequestContext.getLocale());
      statisticData.addParameter("portalOwner", portalRequestContext.getPortalOwner());
      statisticData.addParameter("portalUri", portalRequestContext.getPortalURI());
      statisticData.addParameter("pageTitle", portalRequestContext.getTitle());

      UIPortal uiportal = Util.getUIPortal();
      if (uiportal != null) {
        UserNode node = uiportal.getSelectedUserNode();
        if (node != null) {
          statisticData.addParameter("pageUri", node.getURI());
          statisticData.addParameter("pageName", node.getName());
        }
      }

      HttpServletResponse httpResponse = portalRequestContext.getResponse();
      if (httpResponse != null) {
        statisticData.addParameter("httpResponseContentType", httpResponse.getContentType());
        statisticData.addParameter("httpResponseContentLength", httpResponse.getBufferSize());
        statisticData.addParameter("httpResponseStatus", httpResponse.getStatus());

        if (httpResponse.getStatus() >= 400) {
          statisticData.setErrorCode(httpResponse.getStatus());
          statisticData.setStatus(StatisticStatus.KO);
        } else {
          statisticData.setStatus(StatisticStatus.OK);
        }
      }

      statisticData.setDuration(getDuration());
      return statisticData;
    } catch (Exception e) {
      LOG.debug("Error computing page statistics", e);
      return null;
    }
  }

  private long getDuration() {
    Long startTime = operationStartTime.get();
    if (startTime == null) {
      return 0;
    }
    operationStartTime.remove();
    return System.currentTimeMillis() - startTime;
  }

}
