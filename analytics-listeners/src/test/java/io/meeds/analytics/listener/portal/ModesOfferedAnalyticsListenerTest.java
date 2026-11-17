/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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
package io.meeds.analytics.listener.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.portal.theme.service.ThemeService;

@ExtendWith(MockitoExtension.class)
class ModesOfferedAnalyticsListenerTest {

  @Mock
  private ListenerService                listenerService;

  @InjectMocks
  private ModesOfferedAnalyticsListener  listener;

  @Test
  void registersOnTheModesOfferedEvent() {
    listener.init();
    verify(listenerService).addListener(ThemeService.MODES_OFFERED_UPDATED_EVENT, listener);
  }

  @Test
  void recordsTheAdministratorTheModesAndTheThemeInUse() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId("admin")).thenReturn(3L);

      listener.onEvent(new Event<>(ThemeService.MODES_OFFERED_UPDATED_EVENT,
                                   "admin",
                                   Map.of(ThemeService.MODES_OFFERED_DATA_KEY, "LIGHT", ThemeService.THEME_IN_USE_ID_DATA_KEY, 7L)));

      ArgumentCaptor<StatisticData> captor = ArgumentCaptor.forClass(StatisticData.class);
      utils.verify(() -> AnalyticsUtils.addStatisticData(captor.capture()));
      StatisticData data = captor.getValue();
      assertEquals("portal", data.getModule());
      assertEquals("branding", data.getSubModule());
      assertEquals("modesOfferedChanged", data.getOperation());
      assertEquals(3L, data.getUserId());
      assertEquals("LIGHT", data.getParameters().get("modesOffered"));
      assertEquals("7", data.getParameters().get("themeInUse"));
    }
  }

  @Test
  void recordsNothingWithoutIdentity() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId("ghost")).thenReturn(0L);

      listener.onEvent(new Event<>(ThemeService.MODES_OFFERED_UPDATED_EVENT, "ghost", Map.of(ThemeService.MODES_OFFERED_DATA_KEY, "DARK")));

      utils.verify(() -> AnalyticsUtils.addStatisticData(any()), never());
    }
  }

  @Test
  void recordsNothingWithoutData() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId("admin")).thenReturn(3L);

      listener.onEvent(new Event<>(ThemeService.MODES_OFFERED_UPDATED_EVENT, "admin", null));

      utils.verify(() -> AnalyticsUtils.addStatisticData(any()), never());
    }
  }

}
