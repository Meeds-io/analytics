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
package io.meeds.analytics.api.websocket;

import org.cometd.bayeux.server.ServerChannel;
import org.mortbay.cometd.continuation.EXoContinuationBayeux;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.ws.frameworks.cometd.ContinuationService;

import io.meeds.analytics.api.websocket.listener.AnalyticsWebSocketListener;
import io.meeds.analytics.model.UserSettings;

import jakarta.annotation.PostConstruct;

@Component
public class AnalyticsWebSocketService {

  public static final String    COMETD_CHANNEL = "/service/analytics";

  private static final Log      LOG            = ExoLogger.getLogger(AnalyticsWebSocketService.class);

  private ListenerService       listenerService;

  private ContinuationService   continuationService;

  private EXoContinuationBayeux continuationBayeux;

  private ServerChannel         channel;

  private String                cometdContextName;

  @PostConstruct
  public void init() {
    if (channel == null) {
      channel = getContinuationBayeux().createChannelIfAbsent(COMETD_CHANNEL).getReference();
      channel.addListener(new AnalyticsWebSocketListener(this, getListenerService()));
    }
  }

  public UserSettings getUserSettings(String username) {
    UserSettings userSettings = new UserSettings();
    userSettings.setCometdToken(getUserToken(username));
    userSettings.setCometdContext(getCometdContextName());
    return userSettings;
  }

  public String getUserToken(String username) {
    try {
      return getContinuationService().getUserToken(username);
    } catch (Exception e) {
      LOG.warn("Could not retrieve continuation token for user " + username, e);
      return "";
    }
  }

  private ListenerService getListenerService() {
    if (listenerService == null) {
      listenerService = CommonsUtils.getService(ListenerService.class);
    }
    return listenerService;
  }

  private String getCometdContextName() {
    if (cometdContextName == null) {
      cometdContextName = (getContinuationBayeux() == null ? "cometd" : getContinuationBayeux().getCometdContextName());
    }
    return cometdContextName;
  }

  private EXoContinuationBayeux getContinuationBayeux() {
    if (continuationBayeux == null) {
      continuationBayeux = CommonsUtils.getService(EXoContinuationBayeux.class);
    }
    return continuationBayeux;
  }

  private ContinuationService getContinuationService() {
    if (continuationService == null) {
      continuationService = CommonsUtils.getService(ContinuationService.class);
    }
    return continuationService;
  }

}
