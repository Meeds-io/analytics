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
package io.meeds.analytics.oauth.service;

import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_CONSENT_USE;
import static io.meeds.analytics.oauth.util.Utils.OPERATION_OAUTH_TOKEN_USE;
import static io.meeds.analytics.oauth.util.Utils.PARAM_OAUTH_CLIENT_ID;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_OPERATION;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_USER_ID;
import static io.meeds.analytics.utils.AnalyticsUtils.VALUES_SEPARATOR;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ISO8601;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import io.meeds.oauth2.server.plugin.OAuthClientAclPlugin;

@Service
public class AnalyticsOAuthConsentService {

  @Autowired
  private AnalyticsService   analyticsService;

  @Autowired
  private UserACL            userAcl;

  @Autowired
  private IdentityManager    identityManager;

  /**
   * @param clientId OAuth Client IDentifier
   * @param username Username to filter
   * @param remoteUsername user making the operation
   * @return Last used time in format ISO8601
   * @throws IllegalAccessException when not allowed to retirve the information
   * @throws ObjectNotFoundException when username not found
   */
  public String getClientLastUsage(String clientId, String username, String remoteUsername) throws IllegalAccessException,
                                                                                            ObjectNotFoundException {
    boolean hasEditPermission = userAcl.hasEditPermission(OAuthClientAclPlugin.OBJECT_TYPE,
                                                          clientId,
                                                          remoteUsername);
    if (StringUtils.isBlank(username) && !hasEditPermission) {
      throw new IllegalAccessException("Client overall Last usage information is retricted");
    } else if (!StringUtils.equals(username, remoteUsername) && !hasEditPermission) {
      throw new IllegalAccessException("Client Last usage for other users is retricted");
    }
    List<AnalyticsFieldFilter> filters = new ArrayList<>();
    filters.add(new AnalyticsFieldFilter(FIELD_OPERATION,
                                         AnalyticsFieldFilterType.IN_SET,
                                         StringUtils.join(new String[] {
                                           OPERATION_OAUTH_CONSENT_USE,
                                           OPERATION_OAUTH_TOKEN_USE
                                         }, VALUES_SEPARATOR)));
    filters.add(new AnalyticsFieldFilter(PARAM_OAUTH_CLIENT_ID, AnalyticsFieldFilterType.EQUAL, clientId));
    if (StringUtils.isNotBlank(username)) {
      Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
      if (userIdentity == null) {
        throw new ObjectNotFoundException("User with name %s doesn't exist".formatted(username));
      }
      filters.add(new AnalyticsFieldFilter(FIELD_USER_ID, AnalyticsFieldFilterType.EQUAL, userIdentity.getId()));
    }
    List<StatisticData> statisticDatas = analyticsService.retrieveData(filters, 0l, 1l, ZoneId.systemDefault());
    return CollectionUtils.isEmpty(statisticDatas) ? null : formatDate(statisticDatas.get(0).getTimestamp());
  }

  private String formatDate(long timestamp) {
    Calendar instance = Calendar.getInstance();
    instance.setTimeInMillis(timestamp);
    return ISO8601.format(instance);
  }

}
