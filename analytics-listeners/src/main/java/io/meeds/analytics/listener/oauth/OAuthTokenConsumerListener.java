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

import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.MODULE_OAUTH;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.OPERATION_OAUTH_USE_TOKEN;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CLIENT_ID;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CLIENT_NAME;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CLIENT_SCOPES;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CONSENT_SCOPES;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_TOKEN_TYPE;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.SUB_MODULE_OAUTH_AUTHORIZATION;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.oauth2.server.util.OAuthEventType.TOKEN_USED;

import org.springframework.beans.factory.annotation.Autowired;
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
import io.meeds.oauth2.server.model.OAuthConsent;
import io.meeds.oauth2.server.service.OAuthClientService;
import io.meeds.oauth2.server.service.OAuthConsentService;

import jakarta.annotation.PostConstruct;

@Component
@Asynchronous
public class OAuthTokenConsumerListener implements ListenerBase<OAuth2Authorization, String> {

  @Autowired
  private ListenerService     listenerService;

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private OAuthConsentService oAuthConsentService;

  @Autowired
  private OAuthClientService  oAuthClientService;

  @PostConstruct
  public void init() {
    listenerService.addListener(TOKEN_USED, this);
  }

  @Override
  public void onEvent(Event<OAuth2Authorization, String> event) throws Exception {
    OAuth2Authorization oAuth2Authorization = event.getSource();
    String tokenType = event.getData();
    if (oAuth2Authorization == null || oAuth2Authorization.getAccessToken() == null) {
      return;
    }
    String username = oAuth2Authorization.getPrincipalName();
    String clientId = oAuth2Authorization.getRegisteredClientId();
    RegisteredClient client = oAuthClientService.getClient(clientId, true);
    OAuthConsent consent = oAuthConsentService.getConsent(username, clientId);
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    if (consent != null && userIdentity != null) {
      StatisticData statisticData = new StatisticData();
      statisticData.setUserId(userIdentity.getIdentityId());
      statisticData.setModule(MODULE_OAUTH);
      statisticData.setSubModule(SUB_MODULE_OAUTH_AUTHORIZATION);
      statisticData.setOperation(OPERATION_OAUTH_USE_TOKEN);
      statisticData.addParameter(PARAM_OAUTH_CONSENT_SCOPES,
                                 consent.scopes()
                                        .stream()
                                        .map(s -> "scope." + s)
                                        .toList());
      statisticData.addParameter(PARAM_OAUTH_CLIENT_ID, clientId);
      statisticData.addParameter(PARAM_OAUTH_CLIENT_NAME, client.getClientName());
      if (tokenType != null) {
        statisticData.addParameter(PARAM_OAUTH_TOKEN_TYPE, tokenType);
      }
      statisticData.addParameter(PARAM_OAUTH_CLIENT_SCOPES,
                                 client.getScopes()
                                       .stream()
                                       .map(s -> "scope." + s)
                                       .toList());
      addStatisticData(statisticData);
    }
  }

}
