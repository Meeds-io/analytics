/**
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2022 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.exoplatform.analytics.es;

import static org.exoplatform.analytics.utils.AnalyticsUtils.*;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.picocontainer.Startable;

import org.exoplatform.analytics.api.service.StatisticDataQueueService;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.commons.search.index.impl.ElasticIndexingServiceConnector;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AnalyticsIndexingServiceConnector extends ElasticIndexingServiceConnector implements Startable {

  private static final Log          LOG                             =
                                        ExoLogger.getLogger(AnalyticsIndexingServiceConnector.class);

  public static final String        DEFAULT_ES_INDEX_TEMPLATE       = "analytics_template";

  public static final String        DEFAULT_ES_ANALYTICS_INDEX_NAME = "analytics";

  public static final String        ES_ANALYTICS_INDEX_PREFIX       = "exo.es.analytics.index.prefix";

  public static final String        ES_ANALYTICS_INDEX_TEMPLATE     = "exo.es.analytics.index.template";

  public static final Context       ES_ANALYTICS_CONTEXT            = Context.GLOBAL.id("analytics");

  public static final Scope         ES_ANALYTICS_SCOPE              = Scope.APPLICATION.id("analytics");

  private SettingService            settingService;

  private StatisticDataQueueService analyticsQueueService;

  private String                    indexPrefix;

  private String                    indexTemplate;

  public AnalyticsIndexingServiceConnector(StatisticDataQueueService analyticsQueueService,
                                           SettingService settingService,
                                           InitParams initParams) {
    super(initParams);
    this.settingService = settingService;
    this.analyticsQueueService = analyticsQueueService;
    if (initParams != null) {
      if (initParams.containsKey(ES_ANALYTICS_INDEX_PREFIX)) {
        this.indexPrefix = initParams.getValueParam(ES_ANALYTICS_INDEX_PREFIX).getValue();
      }
      if (initParams.containsKey(ES_ANALYTICS_INDEX_TEMPLATE)) {
        this.indexTemplate = initParams.getValueParam(ES_ANALYTICS_INDEX_TEMPLATE).getValue();
      }
    }
    if (StringUtils.isBlank(this.indexPrefix)) {
      this.indexPrefix = DEFAULT_ES_ANALYTICS_INDEX_NAME;
    }
    if (StringUtils.isBlank(this.indexTemplate)) {
      this.indexTemplate = DEFAULT_ES_INDEX_TEMPLATE;
    }
  }

  @Override
  public void start() {
    SettingValue<?> indexTemplateValue = this.settingService.get(ES_ANALYTICS_CONTEXT,
                                                                 ES_ANALYTICS_SCOPE,
                                                                 ES_ANALYTICS_INDEX_TEMPLATE);
    if (indexTemplateValue != null && indexTemplateValue.getValue() != null) {
      String storedIndexTemplate = indexTemplateValue.getValue().toString();
      if (!StringUtils.equals(storedIndexTemplate, indexTemplate)) {
        LOG.warn("Can't change index template from {} to {}. New index will be ignored.", storedIndexTemplate, indexTemplate);
        indexTemplate = storedIndexTemplate;
      }
    }
  }

  @Override
  public void stop() {
    // Nothing to stop
  }

  @Override
  public String getConnectorName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getMapping() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Document create(String idString) {
    if (StringUtils.isBlank(idString)) {
      throw new IllegalArgumentException("id is mandatory");
    }
    long id = Long.parseLong(idString);
    StatisticData data = this.analyticsQueueService.get(id);
    if (data == null) {
      LOG.warn("Can't find document with id {}", id);
      return null;
    }
    String timestampString = String.valueOf(data.getTimestamp());

    Map<String, String> fields = new HashMap<>();
    fields.put("id", idString);
    fields.put(FIELD_TIMESTAMP, timestampString);
    fields.put(FIELD_USER_ID, String.valueOf(data.getUserId()));
    fields.put(FIELD_SPACE_ID, String.valueOf(data.getSpaceId()));
    fields.put(FIELD_MODULE, data.getModule());
    fields.put(FIELD_SUB_MODULE, data.getSubModule());
    fields.put(FIELD_OPERATION, data.getOperation());
    fields.put(FIELD_STATUS, String.valueOf(data.getStatus().ordinal()));
    fields.put(FIELD_ERROR_CODE, String.valueOf(data.getErrorCode()));
    fields.put(FIELD_ERROR_MESSAGE, data.getErrorMessage());
    fields.put(FIELD_DURATION, String.valueOf(data.getDuration()));
    fields.put(FIELD_IS_ANALYTICS, "true");
    if (data.getParameters() != null && !data.getParameters().isEmpty()) {
      fields.putAll(data.getParameters());
    }
    Document esDocument = new Document(String.valueOf(id),
                                       null,
                                       null,
                                       (Set<String>) null,
                                       fields);
    if (data.getListParameters() != null && !data.getListParameters().isEmpty()) {
      esDocument.setListFields(data.getListParameters());
    }
    return esDocument;
  }

  @Override
  public Document update(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getAllIds(int offset, int limit) {
    throw new UnsupportedOperationException();
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public String getIndexTemplate() {
    return indexTemplate;
  }

  public void storeCreatedIndexTemplate() {
    this.settingService.set(ES_ANALYTICS_CONTEXT,
                            ES_ANALYTICS_SCOPE,
                            ES_ANALYTICS_INDEX_TEMPLATE,
                            SettingValue.create(indexTemplate));
  }
}
