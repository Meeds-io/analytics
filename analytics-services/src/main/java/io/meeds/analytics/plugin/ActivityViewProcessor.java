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
package io.meeds.analytics.plugin;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.social.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.collections4.CollectionUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.BaseActivityProcessorPlugin;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.storage.api.ActivityStorage;
import org.exoplatform.social.core.storage.cache.CachedActivityStorage;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ActivityViewProcessor extends BaseActivityProcessorPlugin {

  private static final String      FIELD_VIEWER_IDS        = "viewerIds";

  private static final Log         LOG                     = ExoLogger.getLogger(ActivityViewProcessor.class);

  private static final String      ACTIVITY_PROCESSOR_NAME = "ActivityViewProcessor";

  private static final String      METADATA_NAME           = "viewers";

  public static final MetadataType METADATA_TYPE           = new MetadataType(METADATA_NAME.hashCode(), METADATA_NAME);

  @Autowired
  private MetadataService          metadataService;

  @Autowired
  private ActivityManager          activityManager;

  @Autowired
  private AnalyticsService         analyticsService;

  @Autowired
  private ActivityStorage activityStorage;

  private CachedActivityStorage cachedActivityStorage;

  public ActivityViewProcessor() {
    super(initParams());
  }

  @Override
  public String getName() {
    return ACTIVITY_PROCESSOR_NAME;
  }

  @PostConstruct
  public void init() {
    if (activityStorage instanceof CachedActivityStorage cachedStorage) {
      this.cachedActivityStorage = cachedStorage;
    }
    activityManager.addProcessor(this);
  }

  @Override
  public void processActivity(ExoSocialActivity activity) {
    String authorId = activity.getUserId();
    MetadataKey metadataKey = new MetadataKey(METADATA_TYPE.getName(), METADATA_NAME, Long.parseLong(authorId));
    List<MetadataItem> viewersIdentityIds = metadataService.getMetadataItemsByMetadataAndObject(metadataKey, activity.getMetadataObject());
    if (CollectionUtils.isEmpty(viewersIdentityIds)) {
      AnalyticsFilter filter = new AnalyticsFilter();
      filter.addEqualFilter("operation", "markAsRead");
      filter.addEqualFilter("entityType", "activity");
      filter.addEqualFilter("entityId", activity.getId());
      AnalyticsAggregation userAgg = new AnalyticsAggregation("userId");
      filter.addXAxisAggregation(userAgg);

      ChartDataList chartData = analyticsService.computeChartData(filter);

      List<Long> viewerIds = chartData.getAggregationLabels().stream()
                                                             .flatMap(label -> label.getAggregationValues().stream())
                                                             .map(value -> Long.parseLong(value.getFieldValue()))
                                                             .filter(viewerId -> !String.valueOf(viewerId).equals(authorId))
                                                             .toList();
      if (CollectionUtils.isNotEmpty(viewerIds)) {
        String jsonIds = JsonUtils.toJsonString(viewerIds);
        Map<String, String> properties = new HashMap<>();
        properties.put(FIELD_VIEWER_IDS, jsonIds);
        try {
          metadataService.createMetadataItem(activity.getMetadataObject(), metadataKey, properties);
        } catch (ObjectAlreadyExistsException e) {
          LOG.warn("Viewers metadata already exists for activity {}", activity.getId(), e);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void addActivityViewer(String activityId, long userIdentityId) {
    ExoSocialActivity activity = activityManager.getActivity(activityId);
    String authorId = activity.getUserId();
    MetadataKey viewersMetadataKey = new MetadataKey(METADATA_TYPE.getName(), METADATA_NAME, Long.parseLong(authorId));
    List<MetadataItem> viewersMetadataItems = metadataService.getMetadataItemsByMetadataAndObject(viewersMetadataKey, activity.getMetadataObject());
    if (CollectionUtils.isNotEmpty(viewersMetadataItems)) {
      MetadataItem viewersMetadataItem = viewersMetadataItems.get(0);
      Map<String, String> properties = viewersMetadataItem.getProperties();
      String viewerIdsJson = properties.get(FIELD_VIEWER_IDS);
      LinkedHashSet<String> viewerIds = new LinkedHashSet<>();
      if (viewerIdsJson != null && !viewerIdsJson.isEmpty()) {
        viewerIds = JsonUtils.fromJsonString(viewerIdsJson, LinkedHashSet.class);
      }
      String newId = String.valueOf(userIdentityId);
      if (!viewerIds.contains(newId) && !newId.equals(authorId)) {
        viewerIds.add(newId);
      }
      String updatedJson = JsonUtils.toJsonString(viewerIds);
      properties.put(FIELD_VIEWER_IDS, updatedJson);
      viewersMetadataItem.setProperties(properties);
      metadataService.updateMetadataItem(viewersMetadataItem, Long.parseLong(authorId));
    } else {
      Set<Long> viewerIds = new LinkedHashSet<>();
      viewerIds.add(userIdentityId);
      String jsonIds = JsonUtils.toJsonString(viewerIds);
      Map<String, String> properties = new HashMap<>();
      properties.put(FIELD_VIEWER_IDS, jsonIds);
      try {
        metadataService.createMetadataItem(activity.getMetadataObject(), viewersMetadataKey, properties);
      } catch (ObjectAlreadyExistsException e) {
        LOG.warn("Viewers metadata already exists for activity {}", activity.getId(), e);
      }
    }
    clearCache(activity.getId());
  }

  private void clearCache(String activityId) {
    if (cachedActivityStorage != null) {
      cachedActivityStorage.clearActivityCached(activityId);
    }
  }

  private static InitParams initParams() {
    InitParams initParams = new InitParams();
    ValueParam param = new ValueParam();
    param.setName("priority");
    param.setValue("20");
    initParams.addParameter(param);
    return initParams;
  }
}
