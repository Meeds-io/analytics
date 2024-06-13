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

import static io.meeds.analytics.utils.AnalyticsUtils.MAX_BULK_DOCUMENTS;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.plugin.StatisticDataProcessorPlugin;

@Service
public class StatisticDataProcessorService {

  private static final Log                         LOG                        =
                                                       ExoLogger.getLogger(StatisticDataProcessorService.class);

  private static final short                       MAX_PROCESS_ATTEMPTS_COUNT = 5;

  @Autowired(required = false)
  private Collection<StatisticDataProcessorPlugin> dataProcessorPlugins;

  public void addProcessor(StatisticDataProcessorPlugin dataProcessorPlugin) {
    this.dataProcessorPlugins.add(dataProcessorPlugin);
  }

  public Collection<StatisticDataProcessorPlugin> getProcessors() {
    return this.dataProcessorPlugins;
  }

  public StatisticDataProcessorPlugin getProcessor(String id) {
    return this.dataProcessorPlugins.stream()
                                    .filter(processor -> StringUtils.equals(id, processor.getId()))
                                    .findFirst()
                                    .orElse(null);
  }

  public void process(List<? extends StatisticDataQueueEntry> queueEntries) { // NOSONAR
    if (queueEntries == null || queueEntries.isEmpty()) {
      return;
    }

    List<? extends StatisticDataQueueEntry> queueEntriesToProcess = queueEntries.stream()
                                                                                .filter(queueEntry -> !queueEntry.isProcessed()
                                                                                                      && (!queueEntry.isError()
                                                                                                          || queueEntry.getAttemptCount()
                                                                                                              < MAX_PROCESS_ATTEMPTS_COUNT))
                                                                                .toList();
    if (queueEntriesToProcess.isEmpty()) {
      return;
    } else if (queueEntriesToProcess.size() > MAX_BULK_DOCUMENTS) {
      LOG.debug("Processing queue having {} documents with chunk of {}", queueEntriesToProcess.size(), MAX_BULK_DOCUMENTS);

      // Process queue entries by chunk of MAX_BULK_DOCUMENTS elements
      int index = 0;
      do {
        List<? extends StatisticDataQueueEntry> subList = queueEntriesToProcess.stream()
                                                                               .skip(index)
                                                                               .limit(MAX_BULK_DOCUMENTS)
                                                                               .toList();
        process(subList);
        index += subList.size();
      } while (index < queueEntriesToProcess.size());
      return;
    }

    for (StatisticDataProcessorPlugin statisticDataProcessorPlugin : dataProcessorPlugins) {// NOSONAR
                                                                                            // need
                                                                                            // more
                                                                                            // than
                                                                                            // one
                                                                                            // continue
                                                                                            // statement
      String processorId = statisticDataProcessorPlugin.getId();
      List<StatisticDataQueueEntry> processorQueueEntries = queueEntriesToProcess.stream()
                                                                                 .filter(queueEntry -> !isProcessorRun(queueEntry,
                                                                                                                       processorId))
                                                                                 .collect(Collectors.toList());

      if (processorQueueEntries.isEmpty()) {
        continue;
      }

      try {
        statisticDataProcessorPlugin.process(processorQueueEntries);
        processorQueueEntries.forEach(queueEntry -> markProcessorAsSuccess(queueEntry, processorId, dataProcessorPlugins));
      } catch (Exception e) {
        if (LOG.isDebugEnabled()) {
          LOG.warn("Error processing queue entries: {}, try to process entries one by one.", processorQueueEntries, e);
        } else {
          LOG.warn("Error processing queue entries, try to process entries one by one.", e);
        }

        // Try to process queue one by one to not block all queue entries
        processorQueueEntries.forEach(queueEntry -> {
          try {
            statisticDataProcessorPlugin.process(Collections.singletonList(queueEntry));
            markProcessorAsSuccess(queueEntry, processorId, dataProcessorPlugins);
          } catch (Exception exception) {
            LOG.warn("Error processing queue entry {}", queueEntry, exception);
            markProcessorAsError(queueEntry, processorId);
          }
        });
      } finally {
        processorQueueEntries.forEach(queueEntry -> queueEntry.setAttemptCount((short) (queueEntry.getAttemptCount() + 1)));
      }
    }
  }

  private void markProcessorAsSuccess(StatisticDataQueueEntry dataQueueEntry,
                                      String processorId,
                                      Collection<StatisticDataProcessorPlugin> processors) {
    if (dataQueueEntry.getProcessingStatus() == null) {
      dataQueueEntry.setProcessingStatus(new HashMap<>());
    }
    dataQueueEntry.getProcessingStatus().put(processorId, true);
    boolean processedForAll = isProcessedForAll(dataQueueEntry, processors);
    dataQueueEntry.setProcessed(processedForAll);
  }

  private void markProcessorAsError(StatisticDataQueueEntry dataQueueEntry, String processorId) {
    if (dataQueueEntry.getProcessingStatus() == null) {
      dataQueueEntry.setProcessingStatus(new HashMap<>());
    }
    dataQueueEntry.getProcessingStatus().put(processorId, false);
    dataQueueEntry.setError(true);
    if (dataQueueEntry.getAttemptCount() >= MAX_PROCESS_ATTEMPTS_COUNT) {
      LOG.warn("Delete from queue an entry that didn't suceeded to be injected for {} times: {}",
               MAX_PROCESS_ATTEMPTS_COUNT,
               dataQueueEntry);
      dataQueueEntry.setProcessed(true);
    }
  }

  private boolean isProcessedForAll(StatisticDataQueueEntry dataQueueEntry, Collection<StatisticDataProcessorPlugin> processors) {
    if (processors == null || processors.isEmpty()) {
      return true;
    }
    for (StatisticDataProcessorPlugin statisticDataProcessorPlugin : processors) {
      if (!isProcessorRun(dataQueueEntry, statisticDataProcessorPlugin.getId())) {
        return false;
      }
    }
    return true;
  }

  private boolean isProcessorRun(StatisticDataQueueEntry dataQueueEntry, String processorId) {
    Boolean processorStatus =
                            dataQueueEntry.getProcessingStatus() == null ? null :
                                                                         dataQueueEntry.getProcessingStatus().get(processorId);
    return processorStatus != null && processorStatus.booleanValue();
  }

}
