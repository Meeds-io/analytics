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
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.OPERATION_OAUTH_USE_CONSENT;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CLIENT_ID;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CLIENT_NAME;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CLIENT_SCOPES;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.PARAM_OAUTH_CONSENT_SCOPES;
import static io.meeds.analytics.oauth.service.AnalyticsOAuthConsentService.SUB_MODULE_OAUTH_AUTHORIZATION;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.oauth2.server.util.EntityMapper.CLIENT_UUID_SETTING;
import static io.meeds.oauth2.server.util.OAuthEventType.CONSENT_USED;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
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
import io.meeds.oauth2.server.service.OAuthConsentService;

import jakarta.annotation.PostConstruct;

@Component
@Asynchronous
public class OAuthConsentConsumerListener implements ListenerBase<Object, RegisteredClient> {

  @Autowired
  private ListenerService     listenerService;

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private OAuthConsentService oAuthConsentService;

  @PostConstruct
  public void init() {
    listenerService.addListener(CONSENT_USED, this);
  }

  @Override
  public void onEvent(Event<Object, RegisteredClient> event) throws Exception {
    Authentication principal = (Authentication) event.getSource();
    RegisteredClient client = event.getData();
    String username = principal.getName();
    String clientId = getClientId(client);
    OAuthConsent consent = oAuthConsentService.getConsent(username, clientId);
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    if (consent != null && userIdentity != null) {
      StatisticData statisticData = new StatisticData();
      statisticData.setUserId(userIdentity.getIdentityId());
      statisticData.setModule(MODULE_OAUTH);
      statisticData.setSubModule(SUB_MODULE_OAUTH_AUTHORIZATION);
      statisticData.setOperation(OPERATION_OAUTH_USE_CONSENT);
      statisticData.addParameter(PARAM_OAUTH_CONSENT_SCOPES,
                                 consent.scopes()
                                        .stream()
                                        .map(s -> "scope." + s)
                                        .toList());
      statisticData.addParameter(PARAM_OAUTH_CLIENT_ID, clientId);
      statisticData.addParameter(PARAM_OAUTH_CLIENT_NAME, client.getClientName());
      statisticData.addParameter(PARAM_OAUTH_CLIENT_SCOPES,
                                 client.getScopes()
                                       .stream()
                                       .map(s -> "scope." + s)
                                       .toList());
      addStatisticData(statisticData);
    }
  }

  private String getClientId(RegisteredClient client) {
    if (client.getClientSettings().getSetting(CLIENT_UUID_SETTING) != null) {
      return client.getClientSettings().getSetting(CLIENT_UUID_SETTING);
    } else {
      return client.getClientId();
    }
  }

}
