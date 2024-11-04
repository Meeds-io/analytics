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
import static io.meeds.analytics.utils.AnalyticsUtils.getCurrentUserIdentityId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceListenerPlugin;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.model.StatisticData;

import jakarta.annotation.PostConstruct;

@Component
public class AnalyticsSpaceListener extends SpaceListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(AnalyticsSpaceListener.class);

  @Autowired
  private SpaceService     spaceService;

  @PostConstruct
  public void init() {
    spaceService.registerSpaceListenerPlugin(this);
  }

  @Override
  public void spaceAccessEdited(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceAccessEdited");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceBannerEdited(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceBannerEdited");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceCreated");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceRemoved(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceRemoved");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceRenamed");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceDescriptionEdited");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceRegistrationEdited(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceRegistrationEdited");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addSpaceStatisticEvent(event, "spaceAvatarEdited");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addUserStatisticEvent(event, "grantedLead");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void joined(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addUserStatisticEvent(event, "joined");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void left(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addUserStatisticEvent(event, "left");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addUserStatisticEvent(event, "revokedLead");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void addInvitedUser(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addUserStatisticEvent(event, "addInvitedUser");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void addPendingUser(SpaceLifeCycleEvent event) {
    try {
      StatisticData statisticData = addUserStatisticEvent(event, "addPendingUser");
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  private StatisticData addSpaceStatisticEvent(SpaceLifeCycleEvent event, String operation) {
    return buildStatisticData(operation, event.getSpace());
  }

  private StatisticData addUserStatisticEvent(SpaceLifeCycleEvent event, String operation) {
    Space space = event.getSpace();
    return buildStatisticData(operation, space);
  }

  private StatisticData buildStatisticData(String operation, Space space) {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("space");
    statisticData.setOperation(operation);
    statisticData.setUserId(getCurrentUserIdentityId());
    addSpaceStatistics(statisticData, space);
    return statisticData;
  }

  private void handleErrorProcessingOperation(SpaceLifeCycleEvent event, Exception exception) {
    LOG.warn("Error adding Statistic data for space {} with event {}",
             event.getSpace().getId(),
             event.getType(),
             exception);
  }

}
