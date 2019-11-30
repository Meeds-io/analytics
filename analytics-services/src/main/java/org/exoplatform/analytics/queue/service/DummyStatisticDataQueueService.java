package org.exoplatform.analytics.queue.service;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.*;

import org.picocontainer.Startable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.exoplatform.analytics.api.service.StatisticDataProcessorService;
import org.exoplatform.analytics.api.service.StatisticDataQueueService;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.model.StatisticDataQueueEntry;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class DummyStatisticDataQueueService implements StatisticDataQueueService, Startable {

  private static final Log                        LOG                        =
                                                      ExoLogger.getLogger(DummyStatisticDataQueueService.class);

  private static final String                     ANALYTICS_QUEUE_CACHE_NAME = "analytics.queue";

  private ExoCache<Long, StatisticDataQueueEntry> statisticQueueCache        = null;

  private StatisticDataProcessorService           statisticDataProcessorService;

  private ScheduledExecutorService                queueProcessingExecutor    = null;

  private PortalContainer                         container                  = null;

  private BigInteger                              totalExecutionTime         = BigInteger.ZERO;

  private long                                    lastExecutionTime          = 0;

  private long                                    executionCount             = 0;

  public DummyStatisticDataQueueService(PortalContainer container,
                                        StatisticDataProcessorService statisticDataProcessorService,
                                        CacheService cacheService) {
    this.statisticDataProcessorService = statisticDataProcessorService;
    this.container = container;
    this.statisticQueueCache = cacheService.getCacheInstance(ANALYTICS_QUEUE_CACHE_NAME);

    ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("Analytics-ingestor-%d").build();
    this.queueProcessingExecutor = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
  }

  @Override
  public void start() {
    // Can't be job, because each cluster node must process its in-memory queue
    queueProcessingExecutor.scheduleAtFixedRate(() -> {
      ExoContainerContext.setCurrentContainer(this.container);
      RequestLifeCycle.begin(this.container);
      try {
        processQueue();
      } finally {
        RequestLifeCycle.end();
      }
    }, 0, 10, TimeUnit.SECONDS);
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
  public void stop() {
    queueProcessingExecutor.shutdown();
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
