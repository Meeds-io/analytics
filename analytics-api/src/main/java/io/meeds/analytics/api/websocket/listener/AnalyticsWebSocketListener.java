/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.analytics.api.websocket.listener;

import static io.meeds.analytics.api.websocket.AnalyticsWebSocketService.COMETD_CHANNEL;

import org.apache.commons.lang3.StringUtils;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerChannel.MessageListener;
import org.cometd.bayeux.server.ServerMessage.Mutable;
import org.cometd.bayeux.server.ServerSession;

import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.api.websocket.AnalyticsWebSocketService;
import io.meeds.analytics.model.AnalyticsWebSocketMessage;
import io.meeds.analytics.utils.AnalyticsUtils;

public class AnalyticsWebSocketListener implements MessageListener {

  public static final String        EXO_ANALYTICS_MESSAGE_EVENT = "analytics.websocket.messageReceived";

  private static final Log          LOG                         = ExoLogger.getLogger(AnalyticsWebSocketListener.class);

  private AnalyticsWebSocketService analyticsWebSocketService;

  private ListenerService           listenerService;

  public AnalyticsWebSocketListener(AnalyticsWebSocketService analyticsWebSocketService, ListenerService listenerService) {
    this.analyticsWebSocketService = analyticsWebSocketService;
    this.listenerService = listenerService;
  }

  @Override
  public boolean onMessage(ServerSession from, ServerChannel channel, Mutable message) {
    try {
      if (message == null || message.getData() == null) {
        LOG.warn("Empty analytics WebSocket message is received");
        return false;
      }
      if (from == null) {
        LOG.warn("Empty analytics WebSocket session is received");
        return false;
      }
      if (channel == null || !StringUtils.equals(channel.getId(), COMETD_CHANNEL)) {
        LOG.warn("Empty WebSocket channel received");
        return false;
      }
      if (!StringUtils.equals(channel.getId(), COMETD_CHANNEL)) {
        LOG.debug("Not Analytics WebSocket channel");
        return false;
      }

      Object data = message.getData();
      AnalyticsWebSocketMessage wsMessage = AnalyticsUtils.fromJsonString(data.toString(), AnalyticsWebSocketMessage.class);
      if (StringUtils.isBlank(wsMessage.getUserName())) {
        LOG.warn("Empty WebSocket username received");
        return false;
      }
      if (StringUtils.isBlank(wsMessage.getToken())) {
        LOG.warn("Empty WebSocket user token received");
        return false;
      }
      String userToken = analyticsWebSocketService.getUserToken(wsMessage.getUserName());
      if (!StringUtils.equals(userToken, wsMessage.getToken())) {
        LOG.warn("Wrong WebSocket token received for user {}", wsMessage.getUserName());
        return false;
      }

      listenerService.broadcast(EXO_ANALYTICS_MESSAGE_EVENT, this, wsMessage);
      return true;
    } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.warn("Error when parsing analytics ws message", e);
      } else {
        LOG.warn("Error when parsing analytics ws message: {}", e.getMessage());
      }
      return false;
    }
  }
}
