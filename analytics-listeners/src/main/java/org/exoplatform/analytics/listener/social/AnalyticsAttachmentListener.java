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
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.attachment.AttachmentPlugin;
import org.exoplatform.social.attachment.AttachmentService;
import org.exoplatform.social.attachment.model.ObjectAttachmentId;

import java.util.Date;
import java.util.Map;

import static org.exoplatform.analytics.utils.AnalyticsUtils.addStatisticData;

public class AnalyticsAttachmentListener extends Listener<String, ObjectAttachmentId> {
  public static final String      STATISTICS_ATTACH_OPERATION = "attachImages";

  public static final String      STATISTICS_DETACH_OPERATION = "removeImageAttachments";

  public static final String      ATTACHMENT_CREATED_EVENT    = "attachment.created";

  public static final String      ATTACHMENT_DELETED_EVENT    = "attachment.deleted";

  private final AttachmentService attachmentService;

  public AnalyticsAttachmentListener(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @Override
  public void onEvent(Event<String, ObjectAttachmentId> event) throws Exception {

    String username = event.getSource();
    ObjectAttachmentId objectAttachment = event.getData();

    if (objectAttachment == null) {
      return;
    }

    Map<String, AttachmentPlugin> attachmentPlugins = attachmentService.getAttachmentPlugins();
    AttachmentPlugin attachmentPlugin = attachmentPlugins.get(objectAttachment.getObjectType());
    long spaceId = attachmentPlugin.getSpaceId(objectAttachment.getObjectId());

    long userId = AnalyticsUtils.getUserIdentityId(username);

    switch (event.getEventName()) {
    case ATTACHMENT_CREATED_EVENT: {
      buildStatisticData(STATISTICS_ATTACH_OPERATION, spaceId, userId);
      break;
    }
    case ATTACHMENT_DELETED_EVENT: {
      buildStatisticData(STATISTICS_DETACH_OPERATION, spaceId, userId);
      break;
    }
    default:
      throw new IllegalArgumentException("Unexpected listener event name: " + event.getEventName());
    }
  }

  private void buildStatisticData(String operation, long spaceId, long userId) {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("attachment");
    statisticData.setOperation(operation);
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setUserId(userId);
    statisticData.addParameter("spaceId", spaceId);
    addStatisticData(statisticData);
  }
}
