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
package io.meeds.analytics.job;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.common.ContainerTransactional;

import lombok.SneakyThrows;

/**
 * A job to collect statistics of users count
 */
@Configuration
@EnableScheduling
public class SpacesStatisticsCountJob {

  @Autowired
  private SpaceService spaceService;

  @SneakyThrows
  @ContainerTransactional
  @Scheduled(initialDelay = 2, fixedDelay = 180, timeUnit = TimeUnit.MINUTES)
  public void run() {
    long startTime = System.currentTimeMillis();
    int allSpacesCount = spaceService.getAllSpacesWithListAccess().getSize();
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("social");
    statisticData.setSubModule("space");
    statisticData.setOperation("spacesCount");
    statisticData.setDuration(System.currentTimeMillis() - startTime);
    statisticData.addParameter("countType", "allSpaces");
    statisticData.addParameter("count", allSpacesCount);
    AnalyticsUtils.addStatisticData(statisticData);
  }

}
