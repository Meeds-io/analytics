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
package io.meeds.analytics.listener.social;

import java.util.Date;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.social.metadata.tag.model.TagObject;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class AnalyticsActivityTagsListener extends Listener<TagObject, Set<TagName>> {

  private static final String EVENT_NAME = "metadata.tag.added";

  @Autowired
  private ActivityManager     activityManager;

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private ListenerService     listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener(EVENT_NAME, this);
  }

  @Override
  @ExoTransactional
  public void onEvent(Event<TagObject, Set<TagName>> event) throws Exception {
    TagObject tagObject = event.getSource();
    if (StringUtils.equals(tagObject.getType(), ExoSocialActivityImpl.DEFAULT_ACTIVITY_METADATA_OBJECT_TYPE)) {
      addEventStatistic(event.getData(), tagObject.getId());
    }
  }

  private void addEventStatistic(Set<TagName> tagNames, String activityId) {
    int numberOfTags = tagNames.size();
    String objectType;
    ExoSocialActivity activity = activityManager.getActivity(activityId);
    if (activity.getType() == null) {
      objectType = activity.isComment() ? "COMMENT" : "ACTIVITY";
    } else {
      objectType = activity.getType();
    }
    Identity audienceIdentity = activityManager.getActivityStreamOwnerIdentity(activity.getId());
    String username = getActivityPoster(activity);
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("ui");
    statisticData.setOperation("Add tag");
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setUserId(Long.parseLong(activity.getUserId()));
    statisticData.addParameter("username", username);
    statisticData.addParameter("type", objectType);
    statisticData.addParameter("dataType", StringUtils.lowerCase(objectType));
    statisticData.addParameter("spaceId", audienceIdentity.getId());

    for (int i = 0; i < numberOfTags; i++) {
      AnalyticsUtils.addStatisticData(statisticData);
    }
  }

  private String getActivityPoster(ExoSocialActivity activity) {
    Validate.notNull(activity.getUserId(), "activity.getUserId() must not be null!");
    return identityManager.getIdentity(activity.getUserId()).getRemoteId();
  }
}
