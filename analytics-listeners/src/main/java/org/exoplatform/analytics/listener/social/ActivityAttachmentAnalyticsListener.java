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

import static org.exoplatform.analytics.utils.AnalyticsUtils.addActivityStatisticsData;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.social.attachment.AttachmentService;
import org.exoplatform.social.attachment.model.ObjectAttachmentId;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

@Asynchronous
public class ActivityAttachmentAnalyticsListener extends BaseAttachmentAnalyticsListener {

  private ActivityManager activityManager;

  public ActivityAttachmentAnalyticsListener(AttachmentService attachmentService,
                                             SpaceService spaceService,
                                             ActivityManager activityManager,
                                             InitParams initParam) {
    super(attachmentService, spaceService, initParam);
    this.activityManager = activityManager;
  }

  @Override
  protected void extendStatisticData(StatisticData statisticData, ObjectAttachmentId objectAttachment) {
    ExoSocialActivity activity = activityManager.getActivity(objectAttachment.getObjectId());
    if (activity != null) {
      addActivityStatisticsData(statisticData, activity);
    }
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
