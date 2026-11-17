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

import static io.meeds.analytics.utils.AnalyticsUtils.addStatisticData;
import static io.meeds.analytics.utils.AnalyticsUtils.getUserIdentityId;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.model.StatisticData;
import io.meeds.portal.theme.service.ThemeService;

import jakarta.annotation.PostConstruct;

/**
 * Records the display mode a user chose in the user settings. The event is
 * broadcast with the username as source and the mode name (AUTOMATIC, LIGHT
 * or DARK) as data.
 */
@Asynchronous
@Component
public class DisplayModeAnalyticsListener extends Listener<String, String> {

  public static final String OPERATION  = "displayModeChanged";

  public static final String MODE       = "mode";

  private static final Log   LOG        = ExoLogger.getLogger(DisplayModeAnalyticsListener.class);

  @Autowired
  private ListenerService    listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener(ThemeService.DISPLAY_MODE_UPDATED_EVENT, this);
  }

  @Override
  public void onEvent(Event<String, String> event) throws Exception {
    String username = event.getSource();
    if (StringUtils.isBlank(username)) {
      LOG.debug("No user in event {}, nothing recorded", ThemeService.DISPLAY_MODE_UPDATED_EVENT);
      return;
    }
    long userId = getUserIdentityId(username);
    if (userId <= 0) {
      LOG.debug("User {} has no identity, nothing recorded", username);
      return;
    }
    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("branding");
    statisticData.setOperation(OPERATION);
    statisticData.setUserId(userId);
    statisticData.addKeyword(MODE, event.getData());
    addStatisticData(statisticData);
  }

}
