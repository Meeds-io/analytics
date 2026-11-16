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
package io.meeds.analytics.listener.websocket;

import static io.meeds.analytics.utils.AnalyticsUtils.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.listener.*;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.api.websocket.AnalyticsWebSocketService;
import io.meeds.analytics.api.websocket.listener.AnalyticsWebSocketListener;
import io.meeds.analytics.model.AnalyticsWebSocketMessage;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticWatcher;

import jakarta.annotation.PostConstruct;

/**
 * Records the statistics a page sends through the analytics websocket. The
 * page is a trust boundary: its operation and its space come from the
 * browser, so the server decides what it stores. The fixed fields, the
 * storage's own alternative names and the space attributes are never taken
 * from the page; the space is resolved once from every place the page names
 * it; a declared browser operation is stored under its declared module and
 * with the space only for a member of it; a page value is stored only under a
 * name no server writer typed otherwise; and every event gets the origin
 * parameter, so that a query tells it from a server event.
 */
@Asynchronous
@Component
public class WebSocketUIStatisticListener extends Listener<AnalyticsWebSocketService, AnalyticsWebSocketMessage> {
  private static final Log             LOG                 = ExoLogger.getLogger(WebSocketUIStatisticListener.class);

  private static final List<String>    EVENT_NAMES         = Arrays.asList(AnalyticsWebSocketListener.EXO_ANALYTICS_MESSAGE_EVENT);

  private static final String          DEFAULT_MODULE      = "portal";

  private static final String          DEFAULT_SUB_MODULE  = "ui";

  private static final String          PORTAL_URI_PARAMETER = "portalUri";

  private static final String          WATCHER_PARAMETER   = "watcher";

  @Autowired
  private AnalyticsService             analyticsService;

  @Autowired
  private CollaborativeActivityService collaborativeActivityService;

  @Autowired
  private SpaceService                 spaceService;

  @Autowired
  private ListenerService              listenerService;

  @PostConstruct
  public void init() {
    EVENT_NAMES.forEach(name -> listenerService.addListener(name, this));
  }

  @Override
  public void onEvent(Event<AnalyticsWebSocketService, AnalyticsWebSocketMessage> event) throws Exception { // NOSONAR
    AnalyticsWebSocketMessage message = event.getData();
    long userId = getUserIdentityId(message.getUserName());
    if (userId <= 0) {
      LOG.debug("User not found in state, userId= {} ", userId);
      return;
    }

    Map<String, String> data = message.getParameters() == null ? new HashMap<>() : new HashMap<>(message.getParameters());
    dropParameters(data, RESERVED_PARAMETER_NAMES, "a reserved name");
    dropParameters(data, ALTERNATIVE_PARAMETER_NAMES, "the storage's alternative-name suffix");

    Space space = resolveSpace(message, data);
    dropParameters(data, SPACE_PARAMETER_NAMES, "the space family, resolved by the server");
    data.keySet().removeIf(name -> {
      boolean spaceAttribute = SPACE_ATTRIBUTE_NAMES.contains(name);
      if (spaceAttribute) {
        LOG.debug("Page parameter '{}' dropped: a space attribute, written by the server", name);
      }
      return spaceAttribute;
    });

    String module = null;
    String subModule = null;
    String operation = null;
    if (StringUtils.isNotBlank(message.getName())) {
      StatisticWatcher uiWatcher = analyticsService.getUIWatcher(message.getName());
      if (uiWatcher == null) {
        module = DEFAULT_MODULE;
        subModule = DEFAULT_SUB_MODULE;
      } else {
        module = uiWatcher.getModule() == null ? DEFAULT_MODULE : uiWatcher.getModule();
        subModule = uiWatcher.getSubModule() == null ? DEFAULT_SUB_MODULE : uiWatcher.getSubModule();
        operation = uiWatcher.getOperation();

        if (uiWatcher.getParameters() != null && !uiWatcher.getParameters().isEmpty()) {
          data.putAll(uiWatcher.getParameters());
        }
      }
    }

    if (StringUtils.isNotBlank(message.getModule())) {
      module = message.getModule();
    }

    if (StringUtils.isNotBlank(message.getSubModule())) {
      subModule = message.getSubModule();
    }

    if (StringUtils.isNotBlank(message.getOperation())) {
      operation = message.getOperation();
    }

    if (collaborativeActivityService.isBrowserOperation(operation)) {
      // The module of a declared browser operation is the one its addon
      // declares, whatever the page sends; and its space is stored only for a
      // member of it, checked now, since membership changes with time.
      module = collaborativeActivityService.getBrowserOperationModule(operation);
      if (space != null && !spaceService.isMember(space, message.getUserName())) {
        LOG.debug("Browser operation '{}' of user '{}' stored without space {}: not a member",
                  operation,
                  message.getUserName(),
                  space.getId());
        space = null;
      }
    }

    StatisticData statisticData = new StatisticData();
    addSpaceStatistics(statisticData, space);
    statisticData.setModule(module);
    statisticData.setSubModule(subModule);
    statisticData.setOperation(operation);
    statisticData.setUserId(userId);
    if (StringUtils.isNotBlank(message.getPortalUri())) {
      data.put(PORTAL_URI_PARAMETER, message.getPortalUri());
    }
    if (StringUtils.isNotBlank(message.getName())) {
      data.put(WATCHER_PARAMETER, message.getName());
    }
    Set<Entry<String, String>> dataParameters = data.entrySet();
    for (Entry<String, String> dataParameter : dataParameters) {
      String dataParameterName = dataParameter.getKey();
      String dataParameterValue = dataParameter.getValue();
      if (!analyticsService.isKeywordField(dataParameterName)) {
        // A page value is always a keyword: a field a server writer typed
        // otherwise (a long, a boolean, a date) would be rerouted to an
        // alternative name, which every chart would then follow.
        LOG.debug("Page parameter '{}' dropped: the field is not mapped as a keyword", dataParameterName);
        continue;
      }
      if (StringUtils.contains(dataParameterValue, ",")) {
        List<String> dataParameterValues = Arrays.asList(StringUtils.split(dataParameterValue, ","));
        statisticData.addKeywords(dataParameterName, dataParameterValues);
      } else {
        statisticData.addKeyword(dataParameterName, dataParameterValue);
      }
    }
    statisticData.addKeyword(STATISTIC_ORIGIN, STATISTIC_ORIGIN_WEBSOCKET);
    addStatisticData(statisticData);
  }

  /**
   * The one space of the event, from every place the page may name it: the
   * message's space id, its space pretty name and the {@code spaceId}
   * parameter. The same space named twice is that space; a place that names
   * no space at all is ignored; two different spaces, an unknown id, a
   * comma-separated list or a null value resolve to no space.
   */
  private Space resolveSpace(AnalyticsWebSocketMessage message, Map<String, String> data) {
    List<Space> candidates = new ArrayList<>();
    boolean unresolved = false;
    if (StringUtils.isNotBlank(message.getSpaceId())) {
      unresolved |= !addCandidate(candidates, getSpaceById(message.getSpaceId()));
    }
    if (StringUtils.isNotBlank(message.getSpacePrettyName())) {
      unresolved |= !addCandidate(candidates, getSpaceByPrettyName(message.getSpacePrettyName()));
    }
    if (data.containsKey(FIELD_SPACE_ID)) {
      String spaceId = data.get(FIELD_SPACE_ID);
      boolean plainId = StringUtils.isNotBlank(spaceId) && !StringUtils.contains(spaceId, VALUES_SEPARATOR);
      unresolved |= !plainId || !addCandidate(candidates, getSpaceById(spaceId));
    }
    if (unresolved || candidates.isEmpty()) {
      if (unresolved) {
        LOG.debug("Event of user '{}' stored without space: a named space is unknown, listed or null, or two spaces are named",
                  message.getUserName());
      }
      return null;
    }
    Space first = candidates.getFirst();
    boolean same = candidates.stream().allMatch(candidate -> StringUtils.equals(candidate.getId(), first.getId()));
    if (!same) {
      LOG.debug("Event of user '{}' stored without space: two different spaces are named", message.getUserName());
      return null;
    }
    return first;
  }

  private boolean addCandidate(List<Space> candidates, Space space) {
    if (space == null || StringUtils.isBlank(space.getId())) {
      return false;
    }
    candidates.add(space);
    return true;
  }

  private void dropParameters(Map<String, String> data, Pattern namePattern, String reason) {
    data.keySet().removeIf(name -> {
      boolean dropped = namePattern.matcher(name).matches();
      if (dropped) {
        LOG.debug("Page parameter '{}' dropped: {}", name, reason);
      }
      return dropped;
    });
  }

}
