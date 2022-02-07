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

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.profile.ProfileLifeCycleEvent;
import org.exoplatform.social.core.profile.ProfileListenerPlugin;

public class AnalyticsProfileListener extends ProfileListenerPlugin {

  @Override
  public void avatarUpdated(ProfileLifeCycleEvent event) {
    AvatarAttachment avatarAttachment = ((AvatarAttachment) event.getPayload().getProperty(AVATAR));
    if (avatarAttachment != null){
      StatisticData statisticData = buildStatisticData("avatar", event.getSource());
      statisticData.addParameter(IMAGE_SIZE, avatarAttachment.getSize());
      statisticData.addParameter(IMAGE_TYPE, avatarAttachment.getMimeType());
      addStatisticData(statisticData);
    }
  }

  @Override
  public void bannerUpdated(ProfileLifeCycleEvent event) {
    StatisticData statisticData = buildStatisticData("banner", event.getSource());
    addStatisticData(statisticData);
  }

  @Override
  public void contactSectionUpdated(ProfileLifeCycleEvent event) {
    StatisticData statisticData = buildStatisticData("contactSection", event.getSource());
    addStatisticData(statisticData);
  }

  @Override
  public void experienceSectionUpdated(ProfileLifeCycleEvent event) {
    StatisticData statisticData = buildStatisticData("experienceSection", event.getSource());
    addStatisticData(statisticData);
  }

  @Override
  public void createProfile(ProfileLifeCycleEvent event) {
    StatisticData statisticData = buildStatisticData("createProfile", event.getSource());
    addStatisticData(statisticData);
  }

  private StatisticData buildStatisticData(String operation, String username) {
    Identity identity = getIdentity(OrganizationIdentityProvider.NAME, username);
    if (identity == null) {
      return null;
    }
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("profile");
    statisticData.setOperation(operation);
    statisticData.setUserId(Long.parseLong(identity.getId()));
    statisticData.addParameter(FIELD_SOCIAL_IDENTITY_ID, Long.parseLong(identity.getId()));
    statisticData.addParameter("userCreatedDate", identity.getProfile() != null ? identity.getProfile().getCreatedTime() : 0);
    return statisticData;
  }

}
