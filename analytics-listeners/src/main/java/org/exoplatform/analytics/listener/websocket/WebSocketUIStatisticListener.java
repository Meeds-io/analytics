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
package org.exoplatform.analytics.listener.websocket;

import static org.exoplatform.analytics.utils.AnalyticsUtils.*;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.api.service.AnalyticsService;
import org.exoplatform.analytics.api.service.StatisticWatcher;
import org.exoplatform.analytics.api.websocket.AnalyticsWebSocketMessage;
import org.exoplatform.analytics.api.websocket.AnalyticsWebSocketService;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.services.listener.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;

@Asynchronous
public class WebSocketUIStatisticListener extends Listener<AnalyticsWebSocketService, AnalyticsWebSocketMessage> {
  private static final Log LOG = ExoLogger.getLogger(WebSocketUIStatisticListener.class);

  private AnalyticsService analyticsService;

  public WebSocketUIStatisticListener(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @Override
  public void onEvent(Event<AnalyticsWebSocketService, AnalyticsWebSocketMessage> event) throws Exception {
    AnalyticsWebSocketMessage message = event.getData();
    long userId = getUserIdentityId(message.getUserName());
    if (userId <= 0) {
      LOG.debug("User not found in state, userId= {} ", userId);
      return;
    }

    StatisticData statisticData = new StatisticData();
    if (StringUtils.isNotBlank(message.getSpaceId())) {
      Space space = getSpaceById(message.getSpaceId());
      addSpaceStatistics(statisticData, space);
    } else if (StringUtils.isNotBlank(message.getSpacePrettyName())) {
      Space space = getSpaceByPrettyName(message.getSpacePrettyName());
      addSpaceStatistics(statisticData, space);
    }

    String module = null;
    String subModule = null;
    String operation = null;
    Map<String, String> data = message.getParameters();
    if (data == null) {
      data = new HashMap<>();
    }

    if (StringUtils.isNotBlank(message.getName())) {
      StatisticWatcher uiWatcher = analyticsService.getUIWatcher(message.getName());
      if (uiWatcher == null) {
        module = "portal";
        subModule = "ui";
      } else {
        module = uiWatcher.getModule() == null ? "portal" : uiWatcher.getModule();
        subModule = uiWatcher.getSubModule() == null ? "ui" : uiWatcher.getSubModule();
        operation = uiWatcher.getOperation();

        if (uiWatcher.getParameters() != null && !uiWatcher.getParameters().isEmpty()) {
          data.putAll(uiWatcher.getParameters());
        }
      }
    }

    if (StringUtils.isNotBlank(message.getModule())) {
      module = message.getModule();
    }

    if (StringUtils.isNotBlank(message.getSubModule())) {
      subModule = message.getSubModule();
    }

    if (StringUtils.isNotBlank(message.getOperation())) {
      operation = message.getOperation();
    }

    statisticData.setModule(module);
    statisticData.setSubModule(subModule);
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    if (StringUtils.isNotBlank(message.getPortalUri())) {
      data.put("portalUri", message.getPortalUri());
    }
    if (StringUtils.isNotBlank(message.getName())) {
      data.put("watcher", message.getName());
    }
    Set<Entry<String, String>> dataParameters = data.entrySet();
    for (Entry<String, String> dataParameter : dataParameters) {
      String dataParameterName = dataParameter.getKey();
      String dataParameterValue = dataParameter.getValue();
      if (StringUtils.contains(dataParameterValue, ",")) {
        List<String> dataParameterValues = Arrays.asList(StringUtils.split(dataParameterValue, ","));
        statisticData.addParameter(dataParameterName, dataParameterValues);
      } else {
        statisticData.addParameter(dataParameterName, dataParameterValue);
      }
    }
    addStatisticData(statisticData);
  }

}
