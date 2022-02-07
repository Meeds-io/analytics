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
package org.exoplatform.analytics.job;

import org.quartz.*;

import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.*;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * A job to collect statistics of users count
 */
@DisallowConcurrentExecution
public class SpacesStatisticsCountJob implements Job {

  private static final Log LOG = ExoLogger.getLogger(SpacesStatisticsCountJob.class);

  private ExoContainer     container;

  private SpaceService     spaceService;

  public SpacesStatisticsCountJob() {
    this.container = PortalContainer.getInstance();
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    long startTime = System.currentTimeMillis();

    ExoContainer currentContainer = ExoContainerContext.getCurrentContainer();
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(this.container);
    try {
      ListAccess<Space> allSpaces = getSpaceService().getAllSpacesWithListAccess();
      int allSpacesCount = allSpaces.getSize();

      StatisticData statisticData = new StatisticData();
      statisticData.setModule("social");
      statisticData.setSubModule("space");
      statisticData.setOperation("spacesCount");
      statisticData.setDuration(System.currentTimeMillis() - startTime);
      statisticData.addParameter("countType", "allSpaces");
      statisticData.addParameter("count", allSpacesCount);
      AnalyticsUtils.addStatisticData(statisticData);
    } catch (Exception e) {
      LOG.error("Error while computing spaces statistics", e);
    } finally {
      RequestLifeCycle.end();
      ExoContainerContext.setCurrentContainer(currentContainer);
    }
  }

  private SpaceService getSpaceService() {
    if (spaceService == null) {
      spaceService = this.container.getComponentInstanceOfType(SpaceService.class);
    }
    return spaceService;
  }

}
