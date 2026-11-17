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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
class DisplayModeAnalyticsListenerTest {

  @Mock
  private ListenerService                listenerService;

  @InjectMocks
  private DisplayModeAnalyticsListener   listener;

  @Test
  void registersOnTheDisplayModeEvent() {
    listener.init();
    verify(listenerService).addListener(ThemeService.DISPLAY_MODE_UPDATED_EVENT, listener);
  }

  @Test
  void recordsTheUserAndTheNewMode() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId("john")).thenReturn(12L);

      listener.onEvent(new Event<>(ThemeService.DISPLAY_MODE_UPDATED_EVENT, "john", "DARK"));

      ArgumentCaptor<StatisticData> captor = ArgumentCaptor.forClass(StatisticData.class);
      utils.verify(() -> AnalyticsUtils.addStatisticData(captor.capture()));
      StatisticData data = captor.getValue();
      assertEquals("portal", data.getModule());
      assertEquals("branding", data.getSubModule());
      assertEquals("displayModeChanged", data.getOperation());
      assertEquals(12L, data.getUserId());
      assertEquals("DARK", data.getParameters().get("mode"));
    }
  }

  @Test
  void recordsNothingWithoutIdentity() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId("ghost")).thenReturn(0L);

      listener.onEvent(new Event<>(ThemeService.DISPLAY_MODE_UPDATED_EVENT, "ghost", "LIGHT"));

      utils.verify(() -> AnalyticsUtils.addStatisticData(any()), never());
    }
  }

  @Test
  void recordsNothingWithoutUser() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId((String) null)).thenReturn(12L);

      listener.onEvent(new Event<>(ThemeService.DISPLAY_MODE_UPDATED_EVENT, null, "LIGHT"));

      utils.verify(() -> AnalyticsUtils.addStatisticData(any()), never());
    }
  }

  @Test
  void noModeParameterWhenEventHasNoData() throws Exception {
    try (MockedStatic<AnalyticsUtils> utils = mockStatic(AnalyticsUtils.class)) {
      utils.when(() -> AnalyticsUtils.getUserIdentityId("john")).thenReturn(12L);

      listener.onEvent(new Event<>(ThemeService.DISPLAY_MODE_UPDATED_EVENT, "john", null));

      ArgumentCaptor<StatisticData> captor = ArgumentCaptor.forClass(StatisticData.class);
      utils.verify(() -> AnalyticsUtils.addStatisticData(captor.capture()));
      assertNull(captor.getValue().getParameters());
    }
  }

}
