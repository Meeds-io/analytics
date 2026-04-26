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
import static io.meeds.analytics.oauth.util.Utils.*;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_AUTH_METHOD;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_GRANT_TYPES;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_ID;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_NAME;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_REDIRECT_URIS;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_RESPONSE_TYPES;
import static io.meeds.analytics.oauth.util.Utils.SUB_MODULE_OAUTH_CLIENT;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.oauth2.server.util.OAuthEventType.CLIENT_REGISTER_REJECT_EVENT;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.oidc.OidcClientRegistration;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerBase;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticData.StatisticStatus;
import io.meeds.oauth2.server.model.OAuthCimdClientMetadata;

import jakarta.annotation.PostConstruct;

@Component
public class OAuthClientRegisterErrorListener implements ListenerBase<Object, RuntimeException> {

  private static final List<String> EVENT_TYPES = Arrays.asList(CLIENT_REGISTER_REJECT_EVENT);

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_TYPES.forEach(e -> listenerService.addListener(e, this));
  }

  @Override
  public void onEvent(Event<Object, RuntimeException> event) throws Exception {
    Object client = event.getData();
    if (client != null) {
      String operation = OPERATION_OAUTH_CLIENT_REJECT_REGISTER;
      StatisticData statisticData = new StatisticData();
      statisticData.setModule(MODULE_OAUTH);
      statisticData.setSubModule(SUB_MODULE_OAUTH_CLIENT);
      statisticData.setOperation(operation);
      statisticData.setStatus(StatisticStatus.KO);
      statisticData.setErrorMessage(event.getData().getMessage());
      if (client instanceof OAuthCimdClientMetadata cimdClient) {
        addOAuthClientFields(statisticData, cimdClient);
      } else if (client instanceof OidcClientRegistration dcrClient) {
        addOAuthClientFields(statisticData, dcrClient);
      }
      addStatisticData(statisticData);
    }
  }

  private void addOAuthClientFields(StatisticData statisticData, OAuthCimdClientMetadata client) {
    statisticData.addParameter(PARAM_OAUTH_CLIENT_ID, client.clientId());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_NAME, client.clientName());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_REDIRECT_URIS, client.redirectUris());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_AUTH_METHOD, client.tokenEndpointAuthMethod());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_GRANT_TYPES, client.grantTypes());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_RESPONSE_TYPES, client.responseTypes());
  }

  private void addOAuthClientFields(StatisticData statisticData, OidcClientRegistration client) {
    statisticData.addParameter(PARAM_OAUTH_CLIENT_ID, client.getClientId());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_NAME, client.getClientName());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_REDIRECT_URIS, client.getRedirectUris());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_SCOPES, client.getScopes());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_AUTH_METHOD, client.getTokenEndpointAuthenticationMethod());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_GRANT_TYPES, client.getGrantTypes());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_RESPONSE_TYPES, client.getResponseTypes());
  }

}
