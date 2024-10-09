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
package io.meeds.analytics.api.service;

import io.meeds.analytics.model.StatisticData;

/**
 * A service to manage statistic data ingestion processing
 */
public interface StatisticDataQueueService {

  /**
   * Add {@link StatisticData} in statistics data ingestion queue
   * 
   * @param data
   */
  void put(StatisticData data);

  /**
   * Retrieve {@link StatisticData} from queue by its id
   * 
   * @param id unique identifier to retrieve {@link StatisticData} from queue
   * @return {@link StatisticData}
   */
  StatisticData get(long id);

  /**
   * @return statistic data ingestion queue size
   */
  int queueSize();

  /**
   * Retrieve queue elements and process them
   */
  void processQueue();

  /**
   * @return average queue processing execution time
   */
  long getAverageExecutionTime();

  /**
   * @return queue processing operation exectuion count
   */
  long getExecutionCount();

  /**
   * @return latest operation queue processing execution time
   */
  long getLastExecutionTime();
}
