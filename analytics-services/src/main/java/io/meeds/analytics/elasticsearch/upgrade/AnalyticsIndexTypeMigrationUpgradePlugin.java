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
import org.exoplatform.container.xml.InitParams;

/**
 * Runs, after the platform has started, the migration of the weekly analytics
 * indices whose fields differ from the {@code keyword} ones of the newest
 * index, until a run leaves no index behind. The plugin keeps its own record
 * of that run: the upgrade service runs a plugin that failed again only when
 * the version of the platform has changed since, which a restart of the same
 * build is not. A run that follows a complete one is not asked for; one that
 * follows a failed one finds the indices already migrated with the expected
 * types and skips them.
 */
public class AnalyticsIndexTypeMigrationUpgradePlugin extends UpgradeProductPlugin {

  private static final String MIGRATION_DONE = "analyticsIndicesMigrated";

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
    List<String> failedIndices = getMigrationService().migrate();
    if (!failedIndices.isEmpty()) {
      throw new IllegalStateException("The analytics indices " + failedIndices + " are not migrated");
    }
    storeValueForPlugin(MIGRATION_DONE, String.valueOf(true));
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
