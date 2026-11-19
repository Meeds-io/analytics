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
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;

/**
 * Runs once, after the platform has started, the migration of the weekly
 * analytics indices whose field types differ from the newest one's. A run
 * that leaves an index behind fails, so that the upgrade service runs the
 * plugin again at the next start, where the indices already migrated are
 * found with the expected types and skipped.
 */
public class AnalyticsIndexTypeMigrationUpgradePlugin extends UpgradeProductPlugin {

  private final AnalyticsIndexTypeMigrationService migrationService;

  public AnalyticsIndexTypeMigrationUpgradePlugin(AnalyticsIndexTypeMigrationService migrationService,
                                                  SettingService settingService,
                                                  InitParams initParams) {
    super(settingService, initParams);
    this.migrationService = migrationService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    List<String> failedIndices = migrationService.migrate();
    if (!failedIndices.isEmpty()) {
      throw new IllegalStateException("The analytics indices " + failedIndices
          + " keep field types that differ from the newest index's");
    }
  }

}
