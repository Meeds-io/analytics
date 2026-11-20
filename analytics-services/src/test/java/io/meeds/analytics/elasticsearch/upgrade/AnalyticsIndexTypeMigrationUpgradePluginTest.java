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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

import io.meeds.analytics.elasticsearch.model.AnalyticsMigrationRunningException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    plugin = new AnalyticsIndexTypeMigrationUpgradePlugin(settingService, initParams) {
      @Override
      protected AnalyticsIndexTypeMigrationService getMigrationService() {
        return migrationService;
      }
    };
    // A setting service that returns what the plugin stored
    doAnswer(invocation -> {
      SettingValue<?> stored = invocation.getArgument(3);
      doAnswer(read -> stored).when(settingService).get(eq(Context.GLOBAL), any(), eq(invocation.getArgument(2)));
      return null;
    }).when(settingService).set(eq(Context.GLOBAL), any(), any(), any());
  }

  @Test
  void theMigrationIsAskedAgainAtEachStartUntilARunLeavesNoIndexBehind() {
    // What the upgrade service records for a first run with no change of
    // version, which its own check would not ask again
    UpgradePluginExecutionContext sameVersion = new UpgradePluginExecutionContext("7.3.0", 0);
    when(migrationService.migrate()).thenReturn(List.of("analytics_2026-07-30"));

    assertTrue(plugin.shouldProceedToUpgrade("7.3.0", "7.3.0", sameVersion));
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> plugin.processUpgrade("7.3.0", "7.3.0"));

    assertTrue(failure.getMessage().contains("analytics_2026-07-30"), failure::getMessage);
    verify(settingService, never()).set(any(), any(), any(), any());
    assertTrue(plugin.shouldProceedToUpgrade("7.3.0", "7.3.0", sameVersion),
               "a run that left an index behind is asked again, on the same version too");
  }

  @Test
  void theMigrationIsAskedAgainWhileTheNewestIndexDoesNotHaveTheTypesOfTheTemplate() {
    when(migrationService.migrate()).thenReturn(List.of());
    when(migrationService.isNewestIndexTypedAsTheTemplate()).thenReturn(false);

    plugin.processUpgrade("7.2.0", "7.3.0");

    verify(settingService, never()).set(any(), any(), any(), any());
    assertTrue(plugin.shouldProceedToUpgrade("7.3.0", "7.3.0", new UpgradePluginExecutionContext("7.3.0", 1)));
  }

  @Test
  void theMigrationIsAskedAgainWhenAnotherRunHoldsItsLock() {
    when(migrationService.migrate()).thenThrow(new AnalyticsMigrationRunningException("The migration is running."));

    plugin.processUpgrade("7.2.0", "7.3.0");

    verify(settingService, never()).set(any(), any(), any(), any());
    assertTrue(plugin.shouldProceedToUpgrade("7.3.0", "7.3.0", new UpgradePluginExecutionContext("7.3.0", 1)));
  }

  @Test
  void theMigrationIsNotAskedAgainOnceARunLeftNoIndexBehind() {
    when(migrationService.migrate()).thenReturn(List.of());
    when(migrationService.isNewestIndexTypedAsTheTemplate()).thenReturn(true);
    assertTrue(plugin.shouldProceedToUpgrade("7.3.0", "7.2.0", null));

    plugin.processUpgrade("7.2.0", "7.3.0");

    assertFalse(plugin.shouldProceedToUpgrade("7.3.0", "7.2.0", null));
    assertFalse(plugin.shouldProceedToUpgrade("7.4.0", "7.3.0", new UpgradePluginExecutionContext("7.3.0", 1)));
  }

}
