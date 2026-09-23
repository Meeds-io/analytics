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
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.ElasticsearchMappingConflictException;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.plugin.StatisticDataProcessorPlugin;

@Component
public class ElasticsearchStatisticDataProcessorService implements StatisticDataProcessorPlugin {

  private static final Log              LOG                          =
                                            ExoLogger.getLogger(ElasticsearchStatisticDataProcessorService.class);

  @Autowired
  private ElasticsearchAnalyticsStorage elasticsearchStorage;

  @Autowired
  private ElasticsearchAnalyticsService elasticsearchAnalyticsService;

  /**
   * Minimum delay between two mapping reads forced by a refused bulk.
   * <p>
   * Elasticsearch answers the same error type for every document it cannot
   * parse, and a stale mapping view is only one of the causes: a refusal to
   * add new fields past {@code index.mapping.total_fields.limit} carries that
   * same type and no mapping read can fix it. Since every known field is
   * materialised in every new weekly index and the known set is never pruned,
   * a long-lived instance does reach that limit. Without this throttle such a
   * refusal would cost one full alias mapping read, one merge and one
   * settings write <i>per refused document and per attempt</i>, every ten
   * seconds.
   * <p>
   * Kept below the window in which an entry is dropped — the queue flushes
   * every 10s and gives up after 5 attempts — so a genuinely stale view is
   * still re-read at least once before any event is lost.
   */
  @Value("${analytics.mapping.conflictRefresh.minIntervalSeconds:30}")
  private long                          mappingRefreshMinIntervalSeconds;

  private final AtomicLong              lastForcedMappingRefreshTime = new AtomicLong(0);

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
      // The write index may hold a type the cached mapping view does not know
      // yet (a weekly index mapped a field dynamically after a rollover, and
      // the periodic refresh has not run). Read the mapping again and retry
      // the refused documents only: the others were created by the first
      // bulk. The retried values are then built against the real types, so a
      // conflicting one is routed to its alternative field instead of being
      // refused. A second refusal is a real error and propagates.
      List<StatisticDataQueueEntry> refusedEntries = getRefusedEntries(processorQueueEntries, e.getRefusedDocumentIds());
      boolean refreshMapping = forceMappingRefresh();
      LOG.info("Elasticsearch refused {} of {} document(s) while parsing them, retrying them once against {} mapping. Reasons: {}",
               refusedEntries.size(),
               processorQueueEntries.size(),
               refreshMapping ? "a freshly read" : "the view read moments ago",
               e.getReasons());
      elasticsearchStorage.sendCreateBulkDocumentsRequest(refusedEntries,
                                                          elasticsearchAnalyticsService.retrieveMapping(refreshMapping));
    }
  }

  /**
   * @return {@code true} when the mapping view may be read again from
   *         Elasticsearch for this refusal, {@code false} when this path
   *         already forced one less than
   *         {@code analytics.mapping.conflictRefresh.minIntervalSeconds} ago —
   *         the retry then runs against the view that read produced. Only the
   *         reads this path forces are counted: the periodic updater's own
   *         refresh must never suppress one, since a field can acquire its
   *         type in the write index right after it.
   */
  private boolean forceMappingRefresh() {
    long now = System.currentTimeMillis();
    long previous = lastForcedMappingRefreshTime.get();
    if (previous != 0 && (now - previous) < mappingRefreshMinIntervalSeconds * 1000) {
      return false;
    }
    return lastForcedMappingRefreshTime.compareAndSet(previous, now);
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
