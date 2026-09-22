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
package io.meeds.analytics.elasticsearch.service;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.ElasticsearchMappingConflictException;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.plugin.StatisticDataProcessorPlugin;

@Component
public class ElasticsearchStatisticDataProcessorService implements StatisticDataProcessorPlugin {

  private static final Log              LOG = ExoLogger.getLogger(ElasticsearchStatisticDataProcessorService.class);

  @Autowired
  private ElasticsearchAnalyticsStorage elasticsearchStorage;

  @Autowired
  private ElasticsearchAnalyticsService elasticsearchAnalyticsService;

  @Override
  public String getId() {
    return "analytics.processor.elasticsearch";
  }

  @Override
  public void process(List<StatisticDataQueueEntry> processorQueueEntries) {
    try {
      elasticsearchStorage.sendCreateBulkDocumentsRequest(processorQueueEntries,
                                                          elasticsearchAnalyticsService.retrieveMapping(false));
    } catch (ElasticsearchMappingConflictException e) {
      // The write index holds a type the cached mapping view does not know
      // yet (a weekly index mapped a field dynamically after a rollover, and
      // the periodic refresh has not run). Refresh the view and retry once,
      // for the refused documents only: the others were created by the first
      // bulk. The retried values are then built against the real types, so a
      // conflicting one is routed to its alternative field instead of being
      // refused. A second refusal is a real error and propagates.
      List<StatisticDataQueueEntry> refusedEntries = getRefusedEntries(processorQueueEntries, e.getRefusedDocumentIds());
      LOG.info("Elasticsearch refused {} of {} document(s) for a field type conflict, refreshing the mapping and retrying them once. Reasons: {}",
               refusedEntries.size(),
               processorQueueEntries.size(),
               e.getReasons());
      elasticsearchStorage.sendCreateBulkDocumentsRequest(refusedEntries, elasticsearchAnalyticsService.retrieveMapping(true));
    }
  }

  private List<StatisticDataQueueEntry> getRefusedEntries(List<StatisticDataQueueEntry> processorQueueEntries,
                                                          Set<String> refusedDocumentIds) {
    List<StatisticDataQueueEntry> refusedEntries = refusedDocumentIds == null ? List.of() :
                                                                               processorQueueEntries.stream()
                                                                                                    .filter(entry -> refusedDocumentIds.contains(String.valueOf(entry.getId())))
                                                                                                    .toList();
    if (refusedEntries.isEmpty()) {
      // No id carried (a refused item without _id: the storage then carries
      // none at all) or ids matching no entry: retry everything, the
      // documents already created answer a version conflict, which is not
      // an error.
      return processorQueueEntries;
    }
    return refusedEntries;
  }

}
