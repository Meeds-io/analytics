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
package org.exoplatform.analytics.es.processor;

import static org.exoplatform.analytics.utils.AnalyticsUtils.ES_ANALYTICS_PROCESSOR_ID;

import java.util.List;

import org.exoplatform.analytics.api.processor.StatisticDataProcessorPlugin;
import org.exoplatform.analytics.es.AnalyticsESClient;
import org.exoplatform.analytics.model.StatisticDataQueueEntry;

public class ElasticSearchStatisticDataProcessor extends StatisticDataProcessorPlugin {

  private AnalyticsESClient analyticsIndexingClient;

  public ElasticSearchStatisticDataProcessor(AnalyticsESClient analyticsIndexingClient) {
    this.analyticsIndexingClient = analyticsIndexingClient;
  }

  @Override
  public String getId() {
    return ES_ANALYTICS_PROCESSOR_ID;
  }

  @Override
  public void process(List<StatisticDataQueueEntry> processorQueueEntries) {
    analyticsIndexingClient.sendCreateBulkDocumentsRequest(processorQueueEntries);
  }

  @Override
  public void init() {
    analyticsIndexingClient.init();
    setInitialized(true);
  }

}
