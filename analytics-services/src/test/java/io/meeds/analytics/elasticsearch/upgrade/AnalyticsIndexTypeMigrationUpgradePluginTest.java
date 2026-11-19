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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

@ExtendWith(MockitoExtension.class)
class AnalyticsIndexTypeMigrationUpgradePluginTest {

  @Mock
  private AnalyticsIndexTypeMigrationService       migrationService;

  @Mock
  private SettingService                           settingService;

  private AnalyticsIndexTypeMigrationUpgradePlugin plugin;

  @BeforeEach
  void setUp() {
    InitParams initParams = new InitParams();
    ValueParam productGroupId = new ValueParam();
    productGroupId.setName("product.group.id");
    productGroupId.setValue("org.exoplatform.social");
    initParams.addParameter(productGroupId);
    plugin = new AnalyticsIndexTypeMigrationUpgradePlugin(migrationService, settingService, initParams);
  }

  @Test
  void theRunSucceedsWhenEveryIndexIsMigrated() {
    when(migrationService.migrate()).thenReturn(List.of());

    assertDoesNotThrow(() -> plugin.processUpgrade("7.2.0", "7.3.0"));
  }

  @Test
  void theRunFailsWhenAnIndexIsLeftBehindSoThatTheNextStartRunsItAgain() {
    when(migrationService.migrate()).thenReturn(List.of("analytics_2026-07-30"));

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> plugin.processUpgrade("7.2.0", "7.3.0"));

    assertTrue(failure.getMessage().contains("analytics_2026-07-30"), failure::getMessage);
  }

}
