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

import org.exoplatform.container.component.BaseComponentPlugin;
import org.exoplatform.container.xml.InitParams;

import lombok.Getter;
import lombok.Setter;

public class StatisticUIWatcherPlugin extends BaseComponentPlugin {

  private static final String PARAM_NAME = "watcher";

  @Getter
  @Setter
  private StatisticWatcher    statisticWatcher;

  public StatisticUIWatcherPlugin(InitParams params) {
    if (params == null || !params.containsKey(PARAM_NAME)) {
      throw new IllegalStateException("'watcher' init param is mandatory");
    }
    this.statisticWatcher = (StatisticWatcher) params.getObjectParam(PARAM_NAME).getObject();
  }
}
