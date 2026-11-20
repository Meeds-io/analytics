/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
package io.meeds.analytics.elasticsearch.upgrade;

import java.util.List;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.AnalyticsMigrationRunningException;

/**
 * Runs, after the platform has started, the migration of the weekly analytics
 * indices that hold as another type a field the index template types
 * {@code keyword}, at each start until the migration is complete: a run left no index
 * behind, and the newest index has the types of the index template. The
 * plugin keeps its own record of it, which replaces the version check of the
 * upgrade service: a first run that fails with no change of version is asked
 * again, a platform whose newest index was created by a former template is
 * asked again until an index created by the new one is the newest, and a
 * complete migration is never asked again, at a later upgrade either. A run
 * that follows an incomplete one finds the indices already migrated with the
 * expected types and skips them.
 * <p>
 * A server stopped while it migrated leaves its lock, which looks held for
 * {@value AnalyticsIndexTypeMigrationService#LOCK_EXPIRY_MS} ms: a start that
 * finds the lock held tries once more in the same start, once that lock can
 * be taken over, so that an interrupted migration does not wait for the
 * start after. When another node does hold the lock, that second try is
 * refused as the first one.
 */
public class AnalyticsIndexTypeMigrationUpgradePlugin extends UpgradeProductPlugin {

  private static final Log    LOG             = ExoLogger.getLogger(AnalyticsIndexTypeMigrationUpgradePlugin.class);

  private static final String MIGRATION_DONE  = "analyticsIndicesMigrated";

  /** Added to the age a lock must reach, so that the try finds it expired */
  private static final long   RETRY_MARGIN_MS = 5000;

  public AnalyticsIndexTypeMigrationUpgradePlugin(SettingService settingService, InitParams initParams) {
    super(settingService, initParams);
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    return !Boolean.parseBoolean(getValue(MIGRATION_DONE));
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    try {
      migrate();
    } catch (AnalyticsMigrationRunningException e) {
      // Another run holds the lock, in this server or on another node, or a
      // stopped run left it: this one neither fails nor records anything
      if (e.getRetryAfterMs() > 0) {
        long delayMs = e.getRetryAfterMs() + RETRY_MARGIN_MS;
        LOG.warn("{}. The migration of the analytics indices is NOT done: it is tried again in {} ms, once its lock can be taken over",
                 e.getMessage(),
                 delayMs);
        scheduleRetry(delayMs, this::retry);
      } else {
        LOG.warn("{}. The migration of the analytics indices is NOT done by this server: it is asked again at the next start if it is not complete by then",
                 e.getMessage());
      }
    }
  }

  private void migrate() {
    AnalyticsIndexTypeMigrationService migrationService = getMigrationService();
    List<String> failedIndices = migrationService.migrate();
    if (!failedIndices.isEmpty()) {
      throw new IllegalStateException("The analytics indices " + failedIndices + " are not migrated");
    }
    if (migrationService.isNewestIndexTypedAsTheTemplate()) {
      storeValueForPlugin(MIGRATION_DONE, String.valueOf(true));
    }
  }

  /** The second and last try of a start, out of the upgrade service's run */
  private void retry() {
    try {
      migrate();
    } catch (AnalyticsMigrationRunningException e) {
      LOG.warn("{}. The migration of the analytics indices is NOT done by this server: it is asked again at the next start if it is not complete by then",
               e.getMessage());
    } catch (Exception e) { // NOSONAR nothing reads the failure of this thread but the log
      LOG.error("The migration of the analytics indices failed: it is asked again at the next start", e);
    }
  }

  /**
   * Runs the second try on a thread of its own, which does not keep the
   * server from stopping, with the container and the request life cycle the
   * upgrade service gives to the first one: its thread is given back at once.
   *
   * @param delayMs the time to wait before the try
   * @param retry   the try
   */
  protected void scheduleRetry(long delayMs, Runnable retry) {
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    Thread thread = new Thread(() -> {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      ExoContainerContext.setCurrentContainer(container);
      RequestLifeCycle.begin(container);
      try {
        retry.run();
      } finally {
        RequestLifeCycle.end();
      }
    }, "analytics-indices-migration-retry");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Read when the upgrade runs, after the start of the platform, and not as a
   * constructor parameter: the upgrade service builds its plugins while the
   * container starts, before the analytics beans are due.
   *
   * @return the service that migrates the indices
   */
  protected AnalyticsIndexTypeMigrationService getMigrationService() {
    return CommonsUtils.getService(AnalyticsIndexTypeMigrationService.class);
  }

}
