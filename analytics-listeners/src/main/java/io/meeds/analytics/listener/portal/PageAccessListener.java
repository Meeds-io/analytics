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
package io.meeds.analytics.listener.portal;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static io.meeds.analytics.utils.AnalyticsUtils.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.mop.user.UserNode;
import org.exoplatform.portal.webui.portal.UIPortal;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.web.application.*;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;

@Component
public class PageAccessListener extends BaseComponentPlugin implements ApplicationLifecycle<RequestContext> {

  private static final Log              LOG                = ExoLogger.getLogger(PageAccessListener.class);

  @Autowired
  private ApplicationLifecycleExtension applicationLifecycleExtension;

  @Value("${analytics.collectAjaxQueries:false}")
  private boolean                       collectAjaxQueries = false;

  private ThreadLocal<Long>             operationStartTime = new ThreadLocal<>();

  @PostConstruct
  public void init() {
    applicationLifecycleExtension.addPortalApplicationLifecycle(this);
  }

  @Override
  public void onInit(Application app) throws Exception {
    // Not used
  }

  @Override
  public void onStartRequest(Application app, RequestContext context) throws Exception {
    operationStartTime.set(System.currentTimeMillis());
  }

  @Override
  public void onEndRequest(Application app, RequestContext context) throws Exception {
    StatisticData statisticData = buildStatisticData(context);
    if (statisticData == null) {
      operationStartTime.remove();
    } else {
      addStatisticData(statisticData);
    }
  }

  @Override
  public void onFailRequest(Application app, RequestContext context, RequestFailure failureType) {
    // Not used
  }

  @Override
  public void onDestroy(Application app) throws Exception {
    // Not used
  }

  private StatisticData buildStatisticData(RequestContext context) {
    try {
      if (!(context instanceof PortalRequestContext portalRequestContext)) {
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
      }
      long userIdentityId = getUserIdentityId(context.getRemoteUser());
      statisticData.setUserId(userIdentityId);

      Space space = SpaceUtils.getSpaceByContext();
      addSpaceStatistics(statisticData, space);

      statisticData.addKeyword("httpRequestMethod", httpRequest.getMethod());
      statisticData.addKeyword("httpRequestUri", httpRequest.getRequestURI());
      statisticData.addKeyword("httpRequestProtocol", httpRequest.getProtocol());
      statisticData.addKeyword("httpRequestContentType", httpRequest.getContentType());
      statisticData.addLong("httpRequestContentLength", httpRequest.getContentLength());

      statisticData.addKeyword("userLocale", portalRequestContext.getLocale());
      statisticData.addKeyword("portalOwner", portalRequestContext.getPortalOwner());
      statisticData.addKeyword("portalUri", portalRequestContext.getPortalURI());
      statisticData.addKeyword("pageTitle", portalRequestContext.getTitle());

      UIPortal uiportal = Util.getUIPortal();
      if (uiportal != null) {
        UserNode node = uiportal.getSelectedUserNode();
        if (node != null) {
          statisticData.addKeyword("pageUri", node.getURI());
          statisticData.addKeyword("pageName", node.getName());
        }
      }

      HttpServletResponse httpResponse = portalRequestContext.getResponse();
      if (httpResponse != null) {
        statisticData.addKeyword("httpResponseContentType", httpResponse.getContentType());
        statisticData.addLong("httpResponseContentLength", httpResponse.getBufferSize());
        statisticData.addKeyword("httpResponseStatus", httpResponse.getStatus());

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
