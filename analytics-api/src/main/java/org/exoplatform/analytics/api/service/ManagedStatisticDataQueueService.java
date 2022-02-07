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
package org.exoplatform.analytics.api.service;

import org.picocontainer.Startable;

import org.exoplatform.management.annotations.Managed;
import org.exoplatform.management.annotations.ManagedDescription;
import org.exoplatform.management.jmx.annotations.NameTemplate;
import org.exoplatform.management.jmx.annotations.Property;

@Managed
@NameTemplate(@Property(key = "service", value = "StatisticDataQueueService"))
@ManagedDescription("Satistics data queue service")
public class ManagedStatisticDataQueueService implements Startable {

  private StatisticDataQueueService statisticDataQueueService;

  public ManagedStatisticDataQueueService(StatisticDataQueueService statisticDataQueueService) {
    this.statisticDataQueueService = statisticDataQueueService;
  }

  @Override
  public void start() {
    // Added as startable to force instantiate it
  }

  @Override
  public void stop() {
    // Added as startable to force instantiate it
  }

  @Managed
  @ManagedDescription("Retrieve queue size")
  public long queueSize() {
    return statisticDataQueueService.queueSize();
  }

  @Managed
  @ManagedDescription("Return average queue processing execution time in ms")
  public long getAverageExecutionTime() {
    return statisticDataQueueService.getAverageExecutionTime();
  }

  @Managed
  @ManagedDescription("Return last queue processing execution time in ms")
  public long getLastExecutionTime() {
    return statisticDataQueueService.getLastExecutionTime();
  }

  @Managed
  @ManagedDescription("Return queue processing execution count")
  public long getExecutionCount() {
    return statisticDataQueueService.getExecutionCount();
  }
}
