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
package io.meeds.analytics.listener.social;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.social.report.model.ActivityReport;

import jakarta.annotation.PostConstruct;

@Component
public class AnalyticsActivityReportListener extends Listener<ActivityReport, Long> {

  private static final String EVENT_ACTIVITY_REPORTED = "social.activity.reported";

  @Autowired
  private ListenerService     listenerService;

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private SpaceService        spaceService;

  @PostConstruct
  public void init() {
    listenerService.addListener(EVENT_ACTIVITY_REPORTED, this);
  }

  @Override
  public void onEvent(Event<ActivityReport, Long> event) throws Exception {
    ActivityReport report = event.getSource();
    StatisticData statisticData = new StatisticData();
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setModule("social");
    statisticData.setSubModule("activity");
    statisticData.setOperation(StringUtils.isBlank(report.getParentObjectId()) ? "reportPost" : "reportComment");
    statisticData.setUserId(report.getReporterIdentityId());
    statisticData.addKeyword("reportReason", report.getReason());
    statisticData.addKeyword("activityId", report.getActivityId());
    if (StringUtils.isNotBlank(report.getParentObjectId())) {
      // for a reported comment, activityId is the comment id: the parent
      // activity id is what an "Activity link" chart dimension needs
      statisticData.addKeyword("parentActivityId", report.getParentObjectId());
    }
    Identity streamOwnerIdentity = identityManager.getIdentity(String.valueOf(report.getStreamOwnerIdentityId()));
    if (streamOwnerIdentity != null) {
      Space space = spaceService.getSpaceByPrettyName(streamOwnerIdentity.getRemoteId());
      if (space != null && space.getId() != null) {
        statisticData.setSpaceId(Long.parseLong(space.getId()));
      }
    }
    AnalyticsUtils.addStatisticData(statisticData);
  }

}
