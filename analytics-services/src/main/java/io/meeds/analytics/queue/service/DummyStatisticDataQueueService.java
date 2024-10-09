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
package io.meeds.analytics.queue.service;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.api.service.StatisticDataProcessorService;
import io.meeds.analytics.api.service.StatisticDataQueueService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.common.ContainerTransactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class DummyStatisticDataQueueService implements StatisticDataQueueService {

  private static final Log                        LOG                        =
                                                      ExoLogger.getLogger(DummyStatisticDataQueueService.class);

  private static final String                     ANALYTICS_QUEUE_CACHE_NAME = "analytics.queue";

  @Autowired
  private StatisticDataProcessorService           statisticDataProcessorService;

  @Autowired
  private CacheService                            cacheService               = null;

  private ExoCache<Long, StatisticDataQueueEntry> statisticQueueCache        = null;

  private ScheduledExecutorService                queueProcessingExecutor    =
                                                                          Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("Analytics-ingestor-%d")
                                                                                                                                               .build());

  private BigInteger                              totalExecutionTime         = BigInteger.ZERO;

  private long                                    lastExecutionTime          = 0;

  private long                                    executionCount             = 0;

  @PostConstruct
  public void init() {
    this.statisticQueueCache = cacheService.getCacheInstance(ANALYTICS_QUEUE_CACHE_NAME);
    // Can't be job, because each cluster node must process its in-memory queue
    this.queueProcessingExecutor.scheduleAtFixedRate(this::processQueueTransactional, 0, 10, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void shutdown() {
    queueProcessingExecutor.shutdown();
  }

  @ContainerTransactional
  public void processQueueTransactional() {
    processQueue();
  }

  @Override
  public void processQueue() {
    this.executionCount++;
    long startTime = System.currentTimeMillis();
    try {
      List<? extends StatisticDataQueueEntry> queueEntries = this.statisticQueueCache.getCachedObjects();
      LOG.debug("Processing {} documents", queueEntries.size());
      statisticDataProcessorService.process(queueEntries);
      for (StatisticDataQueueEntry statisticDataQueueEntry : queueEntries) {
        if (statisticDataQueueEntry.isProcessed()) {
          this.statisticQueueCache.remove(statisticDataQueueEntry.getId());
        }
      }
    } catch (Exception e) {
      LOG.error("Error while processing statistic documents from queue", e);
    } finally {
      this.lastExecutionTime = System.currentTimeMillis() - startTime;
      this.totalExecutionTime = this.totalExecutionTime.add(BigInteger.valueOf(this.lastExecutionTime));
    }
  }

  @Override
  public void put(StatisticData data) {
    StatisticDataQueueEntry statisticDataQueueEntry = new StatisticDataQueueEntry(data);
    this.statisticQueueCache.put(statisticDataQueueEntry.getId(), statisticDataQueueEntry);
  }

  @Override
  public StatisticData get(long id) {
    StatisticDataQueueEntry statisticDataQueueEntry = statisticQueueCache.get(id);
    return statisticDataQueueEntry == null ? null : statisticDataQueueEntry.getStatisticData();
  }

  @Override
  public int queueSize() {
    return this.statisticQueueCache.getCacheSize();
  }

  @Override
  public long getAverageExecutionTime() {
    // Avoid dividing by 0
    if (this.executionCount < 2) {
      return this.totalExecutionTime.longValue();
    }
    return this.totalExecutionTime.divide(BigInteger.valueOf(this.executionCount)).longValue();
  }

  @Override
  public long getExecutionCount() {
    return executionCount;
  }

  @Override
  public long getLastExecutionTime() {
    return lastExecutionTime;
  }
}
