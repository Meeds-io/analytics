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
package io.meeds.analytics.activity.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
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
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ActivityViewersProcessor extends BaseActivityProcessorPlugin {

  private static final Log         LOG                     = ExoLogger.getLogger(ActivityViewersProcessor.class);

  private static final String      ACTIVITY_PROCESSOR_NAME = "ActivityViewersProcessor";

  private static final String      METADATA_NAME           = "viewers";

  public static final MetadataType METADATA_TYPE           = new MetadataType(METADATA_NAME.hashCode(), METADATA_NAME);

  @Autowired
  private MetadataService          metadataService;

  @Autowired
  private ActivityManager          activityManager;

  @Autowired
  private AnalyticsService         analyticsService;

  public ActivityViewersProcessor() {
    super(initParams());
  }

  @Override
  public String getName() {
    return ACTIVITY_PROCESSOR_NAME;
  }

  @PostConstruct
  public void init() {
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
        ObjectMapper mapper = new ObjectMapper();
        String jsonIds = null;
        try {
          jsonIds = mapper.writeValueAsString(viewerIds);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        Map<String, String> properties = new HashMap<>();
        properties.put("viewerIds", jsonIds);
        try {
          metadataService.createMetadataItem(activity.getMetadataObject(), metadataKey, properties);
        } catch (ObjectAlreadyExistsException e) {
          LOG.warn("Viewers metadata already exists for activity {}", activity.getId(), e);
        }
      }
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
