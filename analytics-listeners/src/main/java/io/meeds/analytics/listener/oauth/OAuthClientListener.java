/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.analytics.listener.oauth;

import static io.meeds.analytics.oauth.util.Utils.MODULE_OAUTH;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_CREATE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_DELETE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_DISABLE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_DISPLAY;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_ENABLE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_HIDE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CLIENT_UPDATE;
import static io.meeds.analytics.oauth.util.Utils.SUB_MODULE_OAUTH_CLIENT;
import static io.meeds.analytics.oauth.util.Utils.addOAuthClientFields;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_CREATED_EVENT;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_DELETED_EVENT;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_DISABLED_EVENT;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_DISPLAYED_EVENT;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_ENABLED_EVENT;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_HIDDEN_EVENT;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_UPDATED_EVENT;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;

import jakarta.annotation.PostConstruct;

@Component
public class OAuthClientListener implements ListenerBase<String, RegisteredClient> {

  private static final List<String> EVENT_TYPES = Arrays.asList(CLIENT_CREATED_EVENT,
                                                                CLIENT_UPDATED_EVENT,
                                                                CLIENT_DELETED_EVENT,
                                                                CLIENT_DISABLED_EVENT,
                                                                CLIENT_ENABLED_EVENT,
                                                                CLIENT_DISPLAYED_EVENT,
                                                                CLIENT_HIDDEN_EVENT);

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_TYPES.forEach(e -> listenerService.addListener(e, this));
  }

  @Override
  public void onEvent(Event<String, RegisteredClient> event) throws Exception {
    RegisteredClient client = event.getData();
    if (client != null) {
      String operation = switch (event.getEventName()) {
      case CLIENT_CREATED_EVENT -> OPERATION_OAUTH_CLIENT_CREATE;
      case CLIENT_UPDATED_EVENT -> OPERATION_OAUTH_CLIENT_UPDATE;
      case CLIENT_DELETED_EVENT -> OPERATION_OAUTH_CLIENT_DELETE;
      case CLIENT_DISABLED_EVENT -> OPERATION_OAUTH_CLIENT_DISABLE;
      case CLIENT_ENABLED_EVENT -> OPERATION_OAUTH_CLIENT_ENABLE;
      case CLIENT_DISPLAYED_EVENT -> OPERATION_OAUTH_CLIENT_DISPLAY;
      case CLIENT_HIDDEN_EVENT -> OPERATION_OAUTH_CLIENT_HIDE;
      default -> null;
      };
      if (StringUtils.isNotBlank(operation)) {
        StatisticData statisticData = new StatisticData();
        statisticData.setModule(MODULE_OAUTH);
        statisticData.setSubModule(SUB_MODULE_OAUTH_CLIENT);
        statisticData.setOperation(operation);
        addOAuthClientFields(statisticData, client);
        addStatisticData(statisticData);
      }
    }
  }

}
