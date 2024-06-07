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

import static io.meeds.analytics.utils.AnalyticsUtils.AVATAR;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_SOCIAL_IDENTITY_ID;
import static io.meeds.analytics.utils.AnalyticsUtils.IMAGE_SIZE;
import static io.meeds.analytics.utils.AnalyticsUtils.IMAGE_TYPE;
import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.analytics.utils.AnalyticsUtils.getIdentity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.profile.ProfileLifeCycleEvent;
import org.exoplatform.social.core.profile.ProfileListenerPlugin;

import io.meeds.analytics.model.StatisticData;

import jakarta.annotation.PostConstruct;

@Component
public class AnalyticsProfileListener extends ProfileListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(AnalyticsProfileListener.class);

  @Autowired
  private IdentityManager  identityManager;

  @PostConstruct
  public void init() {
    identityManager.registerProfileListener(this);
  }

  @Override
  public void avatarUpdated(ProfileLifeCycleEvent event) {
    try {
      AvatarAttachment avatarAttachment = ((AvatarAttachment) event.getPayload().getProperty(AVATAR));
      if (avatarAttachment != null) {
        StatisticData statisticData = buildStatisticData("avatar", event.getSource());
        if (statisticData != null) {
          statisticData.addParameter(IMAGE_SIZE, avatarAttachment.getSize());
          statisticData.addParameter(IMAGE_TYPE, avatarAttachment.getMimeType());
          addStatisticData(statisticData);
        }
      }
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void bannerUpdated(ProfileLifeCycleEvent event) {
    try {
      StatisticData statisticData = buildStatisticData("banner", event.getSource());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void contactSectionUpdated(ProfileLifeCycleEvent event) {
    try {
      StatisticData statisticData = buildStatisticData("contactSection", event.getSource());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void experienceSectionUpdated(ProfileLifeCycleEvent event) {
    try {
      StatisticData statisticData = buildStatisticData("experienceSection", event.getSource());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void createProfile(ProfileLifeCycleEvent event) {
    try {
      StatisticData statisticData = buildStatisticData("createProfile", event.getSource());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  private void handleErrorProcessingOperation(ProfileLifeCycleEvent event, Exception exception) {
    LOG.warn("Error adding Statistic data for user {} with event {}",
             event.getUsername(),
             event.getType(),
             exception);
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
