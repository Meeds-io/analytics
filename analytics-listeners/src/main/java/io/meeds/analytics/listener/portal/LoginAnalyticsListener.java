/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;

import io.meeds.analytics.model.StatisticData;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class LoginAnalyticsListener extends Listener<ConversationRegistry, ConversationState> {

  private static final Log          LOG         = ExoLogger.getLogger(LoginAnalyticsListener.class);

  private static final List<String> EVENT_NAMES = Arrays.asList("exo.core.security.ConversationRegistry.register",
                                                                "exo.core.security.ConversationRegistry.unregister");

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(name -> listenerService.addListener(name, this));
  }

  @Override
  public void onEvent(Event<ConversationRegistry, ConversationState> event) throws Exception {
    ConversationState state = event.getData();
    long userId = getUserIdentityId(state);
    if (userId <= 0) {
      LOG.debug("User not found in state, userId= {} ", userId);
      return;
    }
    boolean isLogin = isLogin(event);
    String operation = isLogin ? "login" : "logout";

    StatisticData statisticData = new StatisticData();
    statisticData.setModule("portal");
    statisticData.setSubModule("login");
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    addStatisticData(statisticData);
  }

  private boolean isLogin(Event<ConversationRegistry, ConversationState> event) {
    String eventName = event.getEventName();
    return StringUtils.equals("exo.core.security.ConversationRegistry.register", eventName);
  }

}
