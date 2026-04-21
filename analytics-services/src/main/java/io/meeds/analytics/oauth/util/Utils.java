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
package io.meeds.analytics.oauth.util;

import static io.meeds.oauth2.server.util.EntityMapper.CLIENT_UUID_SETTING;

import java.util.Objects;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import io.meeds.analytics.model.StatisticData;

public class Utils {

  public static final String MODULE_OAUTH                     = "oauth";

  public static final String SUB_MODULE_OAUTH_AUTHORIZATION   = "authorization";

  public static final String OPERATION_OAUTH_CLIENT_CREATE    = "oauthClientCreate";

  public static final String OPERATION_OAUTH_CLIENT_UPDATE    = "oauthClientUpdate";

  public static final String OPERATION_OAUTH_CLIENT_DELETE    = "oauthClientDelete";

  public static final String OPERATION_OAUTH_CLIENT_DISABLE   = "oauthClientDisable";

  public static final String OPERATION_OAUTH_CLIENT_ENABLE    = "oauthClientEnable";

  public static final String OPERATION_OAUTH_CLIENT_DISPLAY   = "oauthClientDisplay";

  public static final String OPERATION_OAUTH_CLIENT_HIDE      = "oauthClientHide";

  public static final String OPERATION_OAUTH_CONSENT_CREATE   = "oauthConsentCreate";

  public static final String OPERATION_OAUTH_CONSENT_UPDATE   = "oauthConsentUpdate";

  public static final String OPERATION_OAUTH_CONSENT_DELETE   = "oauthConsentDelete";

  public static final String OPERATION_OAUTH_CONSENT_USE      = "oauthConsentUse";

  public static final String OPERATION_OAUTH_TOKEN_CREATE     = "oauthTokenCreate";

  public static final String OPERATION_OAUTH_TOKEN_UPDATE     = "oauthTokenUpdate";

  public static final String OPERATION_OAUTH_TOKEN_DELETE     = "oauthTokenDelete";

  public static final String OPERATION_OAUTH_TOKEN_USE        = "oauthTokenUse";

  public static final String PARAM_OAUTH_CLIENT_ID            = "oauthClientId";

  public static final String PARAM_OAUTH_CLIENT_NAME          = "oauthClientName";

  public static final String PARAM_OAUTH_CLIENT_REDIRECT_URIS = "oauthClientRedirectUris";

  public static final String PARAM_OAUTH_CLIENT_SCOPES        = "oauthClientScopes";

  public static final String PARAM_OAUTH_CONSENT_SCOPES       = "oauthConsentScopes";

  public static final String PARAM_OAUTH_TOKEN_ID             = "oauthTokenId";

  public static final String PARAM_OAUTH_TOKEN_TYPE           = "oauthTokenType";

  public static final String PARAM_OAUTH_TOKEN_SCOPES         = "oauthTokenScopes";

  public static final String PARAM_SCOPE_PREFIX               = "scope.";

  private Utils() {
    // Utils Class
  }

  public static void addOAuthClientFields(StatisticData statisticData, RegisteredClient client) {
    statisticData.addParameter(PARAM_OAUTH_CLIENT_ID, getClientId(client));
    statisticData.addParameter(PARAM_OAUTH_CLIENT_NAME, client.getClientName());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_REDIRECT_URIS, client.getRedirectUris());
    statisticData.addParameter(PARAM_OAUTH_CLIENT_SCOPES,
                               client.getScopes()
                                     .stream()
                                     .map(s -> PARAM_SCOPE_PREFIX + s)
                                     .toList());
  }

  public static void addOAuthConsentFields(StatisticData statisticData, OAuth2AuthorizationConsent consent) {
    statisticData.addParameter(PARAM_OAUTH_CONSENT_SCOPES,
                               consent.getScopes()
                                      .stream()
                                      .map(s -> PARAM_SCOPE_PREFIX + s)
                                      .toList());
  }

  public static void addOAuthTokenFields(StatisticData statisticData, OAuth2Authorization token) {
    statisticData.addParameter(PARAM_OAUTH_TOKEN_ID, String.valueOf(Math.abs(Objects.hash(token.getId()))));
    statisticData.addParameter(PARAM_OAUTH_TOKEN_SCOPES,
                               token.getAuthorizedScopes()
                                    .stream()
                                    .map(s -> PARAM_SCOPE_PREFIX + s)
                                    .toList());
  }

  public static String getClientId(RegisteredClient client) {
    if (client.getClientSettings().getSetting(CLIENT_UUID_SETTING) != null) {
      return client.getClientSettings().getSetting(CLIENT_UUID_SETTING);
    } else {
      return client.getClientId();
    }
  }

}
