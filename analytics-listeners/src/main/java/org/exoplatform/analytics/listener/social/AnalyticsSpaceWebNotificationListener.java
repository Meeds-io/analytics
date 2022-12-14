/*
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2020 - 2022 Meeds Association contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.analytics.listener.social;

import static org.exoplatform.analytics.utils.AnalyticsUtils.addSpaceStatistics;
import static org.exoplatform.analytics.utils.AnalyticsUtils.addStatisticData;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.notification.model.SpaceWebNotificationItem;
import org.exoplatform.social.notification.model.SpaceWebNotificationItemUpdate;
import org.exoplatform.social.notification.service.SpaceWebNotificationService;

public class AnalyticsSpaceWebNotificationListener extends Listener<SpaceWebNotificationItem, Long> {

  private SpaceService spaceService;

  public AnalyticsSpaceWebNotificationListener(SpaceService spaceService) {
    this.spaceService = spaceService;
  }

  @Override
  public void onEvent(Event<SpaceWebNotificationItem, Long> event) throws Exception {
    String eventName = event.getEventName();
    SpaceWebNotificationItem spaceWebNotificationItem = event.getSource();
    StatisticData statisticData;
    switch (eventName) {
    case SpaceWebNotificationService.NOTIFICATION_READ_EVENT_NAME:
      statisticData = buildStatisticData("markAsRead",
                                         spaceWebNotificationItem.getSpaceId(),
                                         spaceWebNotificationItem.getUserId());
      break;
    case SpaceWebNotificationService.NOTIFICATION_UNREAD_EVENT_NAME:
      statisticData = buildStatisticData("markAsUnRead",
                                         spaceWebNotificationItem.getSpaceId(),
                                         spaceWebNotificationItem.getUserId());
      break;
    case SpaceWebNotificationService.NOTIFICATION_ALL_READ_EVENT_NAME:
      statisticData = buildStatisticData("markAllAsRead",
                                         spaceWebNotificationItem.getSpaceId(),
                                         spaceWebNotificationItem.getUserId());
      break;
    default:
      return;
    }
    if (StringUtils.isNotBlank(spaceWebNotificationItem.getApplicationName())) {
      statisticData.addParameter("entityType", spaceWebNotificationItem.getApplicationName());
    }
    if (StringUtils.isNotBlank(spaceWebNotificationItem.getApplicationItemId())) {
      statisticData.addParameter("entityId", spaceWebNotificationItem.getApplicationItemId());
    }
    if (spaceWebNotificationItem instanceof SpaceWebNotificationItemUpdate) {
      String userEvent = ((SpaceWebNotificationItemUpdate) spaceWebNotificationItem).getUserEvent();
      if (StringUtils.isNotBlank(userEvent)) {
        statisticData.addParameter("event-type", userEvent);
      }
    }
    addStatisticData(statisticData);
  }

  private StatisticData buildStatisticData(String operation, long spaceId, long userId) {
    StatisticData statisticData = buildStatisticData(operation, spaceService.getSpaceById(String.valueOf(spaceId)), userId);
    statisticData.setSpaceId(spaceId);
    return statisticData;
  }

  private StatisticData buildStatisticData(String operation, Space space, long userId) {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("spaceWebNotifications");
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    addSpaceStatistics(statisticData, space);
    return statisticData;
  }
}
