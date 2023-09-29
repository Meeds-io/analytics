/**
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2022 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.exoplatform.analytics.listener.social;

import static org.exoplatform.analytics.utils.AnalyticsUtils.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.ActivityLifeCycleEvent;
import org.exoplatform.social.core.activity.ActivityListenerPlugin;
import org.exoplatform.social.core.activity.model.ActivityStream;
import org.exoplatform.social.core.activity.model.ActivityStream.Type;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AnalyticsActivityListener extends ActivityListenerPlugin {

  private static final Log   LOG                       = ExoLogger.getLogger(AnalyticsActivityListener.class);

  @Override
  public void saveActivity(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "createActivity");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void updateActivity(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "updateActivity");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }
  @Override
  public void deleteActivity(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event,"deleteActivity");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }
  @Override
  public void shareActivity(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "shareActivity");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void saveComment(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "createComment");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void updateComment(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "updateComment");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }
  @Override
  public void deleteComment(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event,"deleteComment");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }
  @Override
  public void likeActivity(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "likeActivity");
      addLikeIdentityId(statisticData, event);
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void likeComment(ActivityLifeCycleEvent event) {
    try {
      StatisticData statisticData = addActivityStatisticEvent(event, "likeComment");
      addLikeIdentityId(statisticData, event);
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  private void handleErrorProcessingOperation(ActivityLifeCycleEvent event, Exception exception) {
    LOG.warn("Error adding Statistic data for activity {} with event {}", event.getActivityId(), event.getType(), exception);
  }

  private void addLikeIdentityId(StatisticData statisticData, ActivityLifeCycleEvent event) {
    String[] likeIdentityIds = event.getActivity().getLikeIdentityIds();
    if (likeIdentityIds != null && likeIdentityIds.length > 0) {
      String likerId = likeIdentityIds[likeIdentityIds.length - 1];
      statisticData.addParameter("likeIdentityId", likerId);
    }
  }

  private StatisticData addActivityStatisticEvent(ActivityLifeCycleEvent event, String operation) { // NOSONAR
    ExoSocialActivity activity = event.getActivity();

    long modifierUserId = getCurrentUserIdentityId();
    if (modifierUserId == 0) {
      try {
        long identityId = Long.parseLong(activity.getPosterId());
        Identity identity = getIdentity(activity.getPosterId());
        if (identity != null && StringUtils.equals(identity.getProviderId(), OrganizationIdentityProvider.NAME)) {
          modifierUserId = identityId;
        }
      } catch (NumberFormatException e1) {
        modifierUserId = getUserIdentityId(activity.getPosterId());
      }
    }

    if (modifierUserId == 0) {
      modifierUserId = getCurrentUserIdentityId();
    }

    ActivityStream activityStream = activity.getActivityStream();
    if ((activityStream == null || activityStream.getType() == null || activityStream.getPrettyId() == null)
        && StringUtils.isNotBlank(activity.getParentId())) {
      ActivityManager activityManager = CommonsUtils.getService(ActivityManager.class);
      ExoSocialActivity parentActivity = activityManager.getActivity(activity.getParentId());
      activityStream = parentActivity.getActivityStream();
    }

    long streamIdentityId = 0;
    Identity streamIdentity = null;
    if (activityStream != null) {
      Type type = activityStream.getType();
      boolean isSpace = type == Type.SPACE;
      String streamProviderId = isSpace ? SpaceIdentityProvider.NAME : OrganizationIdentityProvider.NAME;
      String streamRemoteId = activityStream.getPrettyId();
      try {
        streamIdentity = getIdentity(streamProviderId, streamRemoteId);
      } catch (Exception e) {
        LOG.debug("Can't retrieve identity with providerId {} and remoteId {}. Attempt to retrieve it as Identity technical ID",
                  streamProviderId,
                  streamRemoteId,
                  e);
        streamIdentity = getIdentity(activityStream.getId());
      }
    }

    StatisticData statisticData = new StatisticData();
    if (streamIdentity != null) {
      streamIdentityId = Long.parseLong(streamIdentity.getId());
      if (StringUtils.equals(streamIdentity.getProviderId(), SpaceIdentityProvider.NAME)) {
        SpaceService spaceService = CommonsUtils.getService(SpaceService.class);
        Space space = spaceService.getSpaceByPrettyName(streamIdentity.getRemoteId());
        addSpaceStatistics(statisticData, space);
      }
    }

    statisticData.setModule("social");
    statisticData.setSubModule("activity");
    statisticData.setOperation(operation);
    statisticData.setUserId(modifierUserId);
    statisticData.addParameter("streamIdentityId", Long.parseLong(streamIdentity.getId()));
    addActivityStatisticsData(statisticData, activity);
    return statisticData;
  }

}
