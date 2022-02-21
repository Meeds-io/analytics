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
package org.exoplatform.analytics.api.processor;

import java.util.List;

import org.exoplatform.analytics.model.StatisticDataQueueEntry;
import org.exoplatform.container.component.BaseComponentPlugin;

public abstract class StatisticDataProcessorPlugin extends BaseComponentPlugin {

  protected boolean initialized;

  protected boolean paused;

  /**
   * @return processor identifier
   */
  public abstract String getId();

  /**
   * Process statistic data
   * 
   * @param processorQueueEntries {@link List} of
   *          {@link StatisticDataQueueEntry}
   */
  public abstract void process(List<StatisticDataQueueEntry> processorQueueEntries);

  /**
   * initializes the processor
   */
  public void init() {
    this.initialized = true;
  }

  public boolean isInitialized() {
    return this.initialized;
  }

  public void setInitialized(boolean initialized) {
    this.initialized = initialized;
  }

  public boolean isPaused() {
    return this.paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

}
