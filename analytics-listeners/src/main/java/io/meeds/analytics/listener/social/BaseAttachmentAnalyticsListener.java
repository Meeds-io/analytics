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

import static io.meeds.analytics.utils.AnalyticsUtils.addSpaceStatistics;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.attachment.AttachmentPlugin;
import org.exoplatform.social.attachment.AttachmentService;
import org.exoplatform.social.attachment.model.ObjectAttachmentId;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.common.ContainerTransactional;

public abstract class BaseAttachmentAnalyticsListener extends Listener<String, ObjectAttachmentId> {

  public static final String STATISTICS_ATTACH_OPERATION = "attachImages";

  public static final String STATISTICS_DETACH_OPERATION = "removeImageAttachments";

  public static final String ATTACHMENT_CREATED_EVENT    = "attachment.created";

  public static final String ATTACHMENT_DELETED_EVENT    = "attachment.deleted";

  @Override
  @ContainerTransactional
  public void onEvent(Event<String, ObjectAttachmentId> event) throws Exception {

    String username = event.getSource();
    ObjectAttachmentId objectAttachment = event.getData();

    if (objectAttachment == null || !getSupportedObjectType().contains(objectAttachment.getObjectType())) {
      return;
    }

    Map<String, AttachmentPlugin> attachmentPlugins = getAttachmentService().getAttachmentPlugins();
    AttachmentPlugin attachmentPlugin = attachmentPlugins.get(objectAttachment.getObjectType());
    long spaceId = attachmentPlugin.getSpaceId(objectAttachment.getObjectId());

    long userId = AnalyticsUtils.getUserIdentityId(username);

    StatisticData statisticData;
    switch (event.getEventName()) {
    case ATTACHMENT_CREATED_EVENT: {
      statisticData = buildStatisticData(STATISTICS_ATTACH_OPERATION, objectAttachment, spaceId, userId);
      break;
    }
    case ATTACHMENT_DELETED_EVENT: {
      statisticData = buildStatisticData(STATISTICS_DETACH_OPERATION, objectAttachment, spaceId, userId);
      break;
    }
    default:
      throw new IllegalArgumentException("Unexpected listener event name: " + event.getEventName());
    }
    extendStatisticData(statisticData, objectAttachment);
    addStatisticData(statisticData);
  }

  private StatisticData buildStatisticData(String operation, ObjectAttachmentId objectAttachment, long spaceId, long userId) {
    Space space = getSpaceService().getSpaceById(String.valueOf(spaceId));
    StatisticData statisticData = new StatisticData();
    statisticData.setModule(getModule(objectAttachment));
    statisticData.setSubModule(getSubModule(objectAttachment));
    statisticData.setOperation(operation);
    statisticData.setTimestamp(new Date().getTime());
    statisticData.setUserId(userId);
    addSpaceStatistics(statisticData, space);
    return statisticData;
  }

  protected void extendStatisticData(StatisticData statisticData, ObjectAttachmentId objectAttachment) {
  }

  protected abstract AttachmentService getAttachmentService();

  protected abstract SpaceService getSpaceService();

  protected abstract List<String> getSupportedObjectType();

  protected abstract String getModule(ObjectAttachmentId objectAttachment);

  protected abstract String getSubModule(ObjectAttachmentId objectAttachment);

}
