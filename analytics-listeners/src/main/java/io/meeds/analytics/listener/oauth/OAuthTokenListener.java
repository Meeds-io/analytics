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
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_TOKEN_CREATE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_TOKEN_DELETE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_TOKEN_UPDATE;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_ID;
import static io.meeds.analytics.oauth.util.Utils.SUB_MODULE_OAUTH_TOKEN;
import static io.meeds.analytics.oauth.util.Utils.addOAuthClientFields;
import static io.meeds.analytics.oauth.util.Utils.addOAuthTokenFields;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.oauth2.server.util.OAuthEventType.TOKEN_CREATED;
import static io.meeds.oauth2.server.util.OAuthEventType.TOKEN_DELETED;
import static io.meeds.oauth2.server.util.OAuthEventType.TOKEN_UPDATED;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.analytics.model.StatisticData;
import io.meeds.common.ContainerTransactional;
import io.meeds.oauth2.server.service.OAuthClientService;

import jakarta.annotation.PostConstruct;

@Component
@Asynchronous
@Profile("auth-server")
public class OAuthTokenListener implements ListenerBase<OAuth2Authorization, OAuth2Authorization> {

  private static final List<String> EVENT_TYPES = Arrays.asList(TOKEN_CREATED,
                                                                TOKEN_UPDATED,
                                                                TOKEN_DELETED);

  @Autowired
  private ListenerService           listenerService;

  @Autowired
  private OAuthClientService        oAuthClientService;

  @Autowired
  private IdentityManager           identityManager;

  @PostConstruct
  public void init() {
    EVENT_TYPES.forEach(e -> listenerService.addListener(e, this));
  }

  @Override
  @ContainerTransactional
  public void onEvent(Event<OAuth2Authorization, OAuth2Authorization> event) throws Exception {
    OAuth2Authorization token = event.getData();
    if (token != null) {
      String operation = switch (event.getEventName()) {
      case TOKEN_CREATED -> OPERATION_OAUTH_TOKEN_CREATE;
      case TOKEN_UPDATED -> OPERATION_OAUTH_TOKEN_UPDATE;
      case TOKEN_DELETED -> OPERATION_OAUTH_TOKEN_DELETE;
      default -> null;
      };
      Identity userIdentity = identityManager.getOrCreateUserIdentity(token.getPrincipalName());
      if (userIdentity != null && StringUtils.isNotBlank(operation)) {
        RegisteredClient client = oAuthClientService.getClient(token.getRegisteredClientId(), true);

        StatisticData statisticData = new StatisticData();
        statisticData.setUserId(userIdentity.getIdentityId());
        statisticData.setModule(MODULE_OAUTH);
        statisticData.setSubModule(SUB_MODULE_OAUTH_TOKEN);
        statisticData.setOperation(operation);
        if (client != null) {
          addOAuthClientFields(statisticData, client);
        } else {
          statisticData.addKeyword(PARAM_OAUTH_CLIENT_ID, token.getRegisteredClientId());
        }
        addOAuthTokenFields(statisticData, token);
        addStatisticData(statisticData);
      }
    }
  }

}
