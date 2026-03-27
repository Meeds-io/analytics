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
package io.meeds.analytics.listener.social;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.notification.model.SpaceWebNotificationItem;

import jakarta.annotation.PostConstruct;

@Asynchronous
@Component
public class AnalyticsViewersListener extends Listener<SpaceWebNotificationItem, Long> {

  private static final Log LOG                    = ExoLogger.getLogger(AnalyticsViewersListener.class);

  private static final List<String> EVENT_NAMES   = List.of("notification.read.item");

  private static final String       METADATA_NAME = "viewers";

  public static final MetadataType METADATA_TYPE  = new MetadataType(METADATA_NAME.hashCode(), METADATA_NAME);

  @Autowired
  private ListenerService listenerService;

  @Autowired
  private ActivityManager activityManager;

  @Autowired
  private MetadataService metadataService;

  @PostConstruct
  public void init() {
        EVENT_NAMES.forEach(name -> listenerService.addListener(name, this));
    }

  @Override
  @ExoTransactional
  public void onEvent(Event<SpaceWebNotificationItem, Long> event) {
    SpaceWebNotificationItem spaceWebNotificationItem = event.getSource();
    updateViewersMetadata(spaceWebNotificationItem);
  }

  private void updateViewersMetadata(SpaceWebNotificationItem spaceWebNotificationItem) {
    ExoSocialActivity activity = activityManager.getActivity(spaceWebNotificationItem.getApplicationItemId());
    MetadataKey viewersMetadataKey = new MetadataKey(METADATA_TYPE.getName(), METADATA_NAME, Long.parseLong(activity.getId()));
    List<MetadataItem> viewersMetadataItems = metadataService.getMetadataItemsByMetadataAndObject(viewersMetadataKey, activity.getMetadataObject());
    if (CollectionUtils.isNotEmpty(viewersMetadataItems)) {
      MetadataItem viewersMetadataItem = viewersMetadataItems.get(0);
      Map<String, String> properties = viewersMetadataItem.getProperties();
      String viewerIdsJson = properties.get("viewerIds");
      List<String> viewerIds;
      if (viewerIdsJson != null && !viewerIdsJson.isEmpty()) {
        try {
          viewerIds = new ArrayList<>(Arrays.asList(
                  new ObjectMapper().readValue(viewerIdsJson, String[].class)
          ));
        } catch (Exception e) {
          viewerIds = new ArrayList<>();
        }
      } else {
        viewerIds = new ArrayList<>();
      }

      String newId = String.valueOf(spaceWebNotificationItem.getUserId());
      if (!viewerIds.contains(newId)) {
        viewerIds.add(newId);
      }
      String updatedJson;
      try {
        updatedJson = new ObjectMapper().writeValueAsString(viewerIds);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
      properties.put("viewerIds", updatedJson);
      viewersMetadataItem.setProperties(properties);
      metadataService.updateMetadataItem(viewersMetadataItem, Long.parseLong(activity.getId()));
    } else {
      List<Long> viewerIds = new ArrayList<>();
      ObjectMapper mapper = new ObjectMapper();
      String jsonIds = null;
      try {
        viewerIds.add(spaceWebNotificationItem.getUserId());
        jsonIds = mapper.writeValueAsString(viewerIds);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
      Map<String, String> properties = new HashMap<>();
      properties.put("viewerIds", jsonIds);
      try {
        metadataService.createMetadataItem(activity.getMetadataObject(), viewersMetadataKey, properties);
      } catch (ObjectAlreadyExistsException e) {
        LOG.warn("Viewers metadata already exists for activity {}", activity.getId(), e);
      }
    }
  }
}