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

import static org.exoplatform.analytics.utils.AnalyticsUtils.addStatisticData;
import static org.exoplatform.analytics.utils.AnalyticsUtils.getCurrentUserIdentityId;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.relationship.RelationshipEvent;
import org.exoplatform.social.core.relationship.RelationshipListenerPlugin;
import org.exoplatform.social.core.relationship.model.Relationship;

public class AnalyticsRelationshipListener extends RelationshipListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(AnalyticsRelationshipListener.class);

  @Override
  public void requested(RelationshipEvent event) {
    try {
      Relationship relationship = event.getPayload();
      StatisticData statisticData = buildStatisticData("requested",
                                                       relationship.getSender().getId(),
                                                       relationship.getReceiver().getId());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void denied(RelationshipEvent event) {
    try {
      Relationship relationship = event.getPayload();
      StatisticData statisticData = buildStatisticData("denied",
                                                       relationship.getSender().getId(),
                                                       relationship.getReceiver().getId());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void confirmed(RelationshipEvent event) {
    try {
      Relationship relationship = event.getPayload();
      StatisticData statisticData = buildStatisticData("confirmed",
                                                       relationship.getSender().getId(),
                                                       relationship.getReceiver().getId());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void ignored(RelationshipEvent event) {
    try {
      Relationship relationship = event.getPayload();
      StatisticData statisticData = buildStatisticData("ignored",
                                                       relationship.getSender().getId(),
                                                       relationship.getReceiver().getId());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  @Override
  public void removed(RelationshipEvent event) {
    try {
      Relationship relationship = event.getPayload();
      StatisticData statisticData = buildStatisticData("removed",
                                                       relationship.getSender().getId(),
                                                       relationship.getReceiver().getId());
      addStatisticData(statisticData);
    } catch (Exception e) {
      handleErrorProcessingOperation(event, e);
    }
  }

  private StatisticData buildStatisticData(String operation, String from, String to) {
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("relationship");
    statisticData.setOperation(operation);
    statisticData.setUserId(getCurrentUserIdentityId());
    statisticData.addParameter("from", from);
    statisticData.addParameter("to", to);
    return statisticData;
  }

  private void handleErrorProcessingOperation(RelationshipEvent event, Exception exception) {
    LOG.warn("Error adding Statistic data for relationship {}/{} with event {}",
             event.getPayload().getSender(),
             event.getPayload().getReceiver(),
             event.getType(),
             exception);
  }

}
