/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2023 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.exoplatform.analytics.listener.social;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.attachment.AttachmentPlugin;
import org.exoplatform.social.attachment.AttachmentService;
import org.exoplatform.social.attachment.model.ObjectAttachmentId;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;

import java.util.Date;
import java.util.Map;

@Asynchronous
public class AnalyticsAttachmentListener extends Listener<String, ObjectAttachmentId> {

  public static final String      TASK_COMMENT_OBJECT_TYPE     = "taskComment";

  public static final String      ACTIVITY_COMMENT_OBJECT_TYPE = "comment";

  public static final String      TASK_OBJECT_TYPE             = "task";

  public static final String      ACTIVITY_OBJECT_TYPE         = "activity";

  public static final String      RULE_OBJECT_TYPE             = "rule";

  public static final String      STATISTICS_OPERATION         = "attachImages";

  private final ActivityManager   activityManager;

  private final AttachmentService attachmentService;

  public AnalyticsAttachmentListener(ActivityManager activityManager, AttachmentService attachmentService) {
    this.activityManager = activityManager;
    this.attachmentService = attachmentService;
  }

  @Override
  @ExoTransactional
  public void onEvent(Event<String, ObjectAttachmentId> event) throws Exception {

    String username = event.getSource();
    ObjectAttachmentId objectAttachment = event.getData();

    if (objectAttachment == null) {
      return;
    }

    String objectModule = getModule(objectAttachment.getObjectType());
    String objectSubModule = getSubModule(objectAttachment.getObjectType(), objectAttachment.getObjectId());

    Map<String, AttachmentPlugin> attachmentPlugins = attachmentService.getAttachmentPlugins();
    AttachmentPlugin attachmentPlugin = attachmentPlugins.get(objectAttachment.getObjectType());
    long spaceId = attachmentPlugin.getSpaceId(objectAttachment.getObjectId());

    StatisticData statisticData = new StatisticData();
    statisticData.setModule(objectModule);
    statisticData.setSubModule(objectSubModule);
    statisticData.setOperation(STATISTICS_OPERATION);
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setUserId(AnalyticsUtils.getUserIdentityId(username));
    statisticData.addParameter("spaceId", spaceId);
  }

  private String getModule(String objectType) {
    return switch (objectType) {
    case TASK_COMMENT_OBJECT_TYPE, TASK_OBJECT_TYPE -> "tasks";
    case ACTIVITY_OBJECT_TYPE -> "social";
    case RULE_OBJECT_TYPE -> "gamification";
    default -> "";
    };
  }

  private String getSubModule(String objectType, String objectId) {
    switch (objectType) {
    case ACTIVITY_OBJECT_TYPE:
      ExoSocialActivity activity = activityManager.getActivity(objectId);
      String objectSubModule;
      if (activity.getType() == null) {
        objectSubModule = activity.isComment() ? ACTIVITY_COMMENT_OBJECT_TYPE : ACTIVITY_OBJECT_TYPE;
      } else {
        objectSubModule = activity.getType();
      }
      return objectSubModule;
    case TASK_OBJECT_TYPE:
      return "task";
    case TASK_COMMENT_OBJECT_TYPE:
      return "comment";
    case RULE_OBJECT_TYPE:
      return "rule";
    default:
      return objectType;
    }
  }
}
