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

import static io.meeds.analytics.utils.AnalyticsUtils.addActivityStatisticsData;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.attachment.AttachmentService;
import org.exoplatform.social.attachment.model.ObjectAttachmentId;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.model.StatisticData;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Asynchronous
@Component
public class ActivityAttachmentAnalyticsListener extends BaseAttachmentAnalyticsListener {

  private static final List<String> SUPPORTED_OBJECT_TYPES = Collections.singletonList("activity");

  private static final List<String> EVENT_NAMES            = Arrays.asList("attachment.created", "attachment.deleted");

  @Getter
  @Autowired
  private AttachmentService         attachmentService;

  @Getter
  @Autowired
  private SpaceService              spaceService;

  @Autowired
  private ActivityManager           activityManager;

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(name -> listenerService.addListener(name, this));
  }

  @Override
  protected void extendStatisticData(StatisticData statisticData, ObjectAttachmentId objectAttachment) {
    ExoSocialActivity activity = activityManager.getActivity(objectAttachment.getObjectId());
    if (activity != null) {
      addActivityStatisticsData(statisticData, activity);
    }
  }

  @Override
  protected List<String> getSupportedObjectType() {
    return SUPPORTED_OBJECT_TYPES;
  }

  @Override
  protected String getModule(ObjectAttachmentId objectAttachment) {
    return "social";
  }

  @Override
  protected String getSubModule(ObjectAttachmentId objectAttachment) {
    return "activity";
  }
}
