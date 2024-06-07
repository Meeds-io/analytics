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
package io.meeds.analytics.job;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.common.ContainerTransactional;

import lombok.SneakyThrows;

/**
 * A job to collect statistics of users count
 */
@Configuration
@EnableScheduling
public class UsersStatisticsCountJob {

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private IdentityManager     identityManager;

  @SneakyThrows
  @ContainerTransactional
  @Scheduled(initialDelay = 2, fixedDelay = 180, timeUnit = TimeUnit.MINUTES)
  public void run() {
    long startTime = System.currentTimeMillis();

    ListAccess<User> allUsers = organizationService.getUserHandler().findAllUsers(UserStatus.ANY);
    int allUsersCount = allUsers.getSize();

    ListAccess<Identity> enabledIdentities = identityManager.getIdentitiesByProfileFilter(OrganizationIdentityProvider.NAME,
                                                                                          null,
                                                                                          false);
    int enabledUsersCount = enabledIdentities.getSize();
    int disabledUsersCount = allUsersCount - enabledUsersCount;

    addUsersCountStatistic("allUsers", allUsersCount, startTime);
    addUsersCountStatistic("enabledUsers", enabledUsersCount, startTime);
    addUsersCountStatistic("disabledUsers", disabledUsersCount, startTime);

    startTime = System.currentTimeMillis();
    Group externalsGroup = organizationService.getGroupHandler().findGroupById("/platform/externals");
    int enabledExternalUsersCount = 0;
    if (externalsGroup != null) {
      ListAccess<Membership> externalMemberships = organizationService.getMembershipHandler()
                                                                      .findAllMembershipsByGroup(externalsGroup);
      enabledExternalUsersCount = externalMemberships.getSize();
    }
    addUsersCountStatistic("enabledExternalUsers", enabledExternalUsersCount, startTime);
    addUsersCountStatistic("enabledInternalUsers", (enabledUsersCount - enabledExternalUsersCount), startTime);
  }

  private void addUsersCountStatistic(String countType, int count, long startTime) {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("account");
    statisticData.setOperation("usersCount");
    statisticData.setDuration(System.currentTimeMillis() - startTime);
    statisticData.addParameter("countType", countType);
    statisticData.addParameter("count", count);
    AnalyticsUtils.addStatisticData(statisticData);
  }

}
