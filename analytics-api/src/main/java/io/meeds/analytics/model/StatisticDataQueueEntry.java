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
package io.meeds.analytics.model;

import java.io.Serializable;
import java.util.Map;

import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class StatisticDataQueueEntry implements Serializable {

  private static final long    serialVersionUID = -4173661081127997921L;

  private StatisticData        statisticData;

  private boolean              processed;

  private boolean              error;

  private short                attemptCount;

  private Map<String, Boolean> processingStatus;

  public StatisticDataQueueEntry(StatisticData statisticData) {
    this.statisticData = statisticData;
  }

  public long getId() {
    return statisticData == null ? 0 : Math.abs(statisticData.computeId());
  }

}
