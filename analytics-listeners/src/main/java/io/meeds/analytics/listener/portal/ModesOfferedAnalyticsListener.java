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

import java.util.Map;

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
 * Records the modes offered to users when an administrator publishes a
 * change of them. The event is broadcast with the administrator's username as
 * source and a map holding the modes offered
 * ({@link ThemeService#MODES_OFFERED_DATA_KEY}) and the theme in use
 * ({@link ThemeService#THEME_IN_USE_ID_DATA_KEY}) as data.
 */
@Asynchronous
@Component
public class ModesOfferedAnalyticsListener extends Listener<String, Map<String, Object>> {

  public static final String OPERATION      = "modesOfferedChanged";

  public static final String MODES_OFFERED  = "modesOffered";

  public static final String THEME_IN_USE   = "themeInUse";

  private static final Log   LOG            = ExoLogger.getLogger(ModesOfferedAnalyticsListener.class);

  @Autowired
  private ListenerService    listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener(ThemeService.MODES_OFFERED_UPDATED_EVENT, this);
  }

  @Override
  public void onEvent(Event<String, Map<String, Object>> event) throws Exception {
    String username = event.getSource();
    Map<String, Object> data = event.getData();
    if (StringUtils.isBlank(username) || data == null) {
      LOG.debug("No user or data in event {}, nothing recorded", ThemeService.MODES_OFFERED_UPDATED_EVENT);
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
    statisticData.addKeyword(MODES_OFFERED, data.get(ThemeService.MODES_OFFERED_DATA_KEY));
    statisticData.addKeyword(THEME_IN_USE, data.get(ThemeService.THEME_IN_USE_ID_DATA_KEY));
    addStatisticData(statisticData);
  }

}
