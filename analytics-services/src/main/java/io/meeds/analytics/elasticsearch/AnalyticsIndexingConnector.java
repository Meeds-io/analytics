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
package io.meeds.analytics.elasticsearch;

import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_DURATION;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_ERROR_CODE;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_ERROR_MESSAGE;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_IS_ANALYTICS;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_MODULE;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_OPERATION;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_SPACE_ID;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_STATUS;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_SUB_MODULE;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_TIMESTAMP;
import static io.meeds.analytics.utils.AnalyticsUtils.FIELD_USER_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.api.service.StatisticDataQueueService;
import io.meeds.analytics.model.StatisticData;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Component
public class AnalyticsIndexingConnector {
//  extends ElasticIndexingServiceConnector
  private static final Log          LOG                         =
                                        ExoLogger.getLogger(AnalyticsIndexingConnector.class);

  public static final String        DEFAULT_ES_INDEX_TEMPLATE   = "analytics_template";

  public static final String        ES_ANALYTICS_INDEX_PREFIX   = "exo.es.analytics.index.prefix";

  public static final String        ES_ANALYTICS_INDEX_TEMPLATE = "exo.es.analytics.index.template";

  public static final Context       ES_ANALYTICS_CONTEXT        = Context.GLOBAL.id("analytics");

  public static final Scope         ES_ANALYTICS_SCOPE          = Scope.APPLICATION.id("analytics");

  @Autowired
  private SettingService            settingService;

  @Autowired
  private StatisticDataQueueService analyticsQueueService;

  @Getter
  @Value("${analytics.es.index.prefix:analytics}")
  private String                    indexPrefix;

  @Getter
  @Value("${analytics.es.index.template:analytics_template}")
  private String                    indexTemplate;

  @Getter
  @Value("${analytics.es.index.alias:analytics_alias}")
  protected String                  indexAlias;

  @Getter
  @Value("${analytics.es.replicas:0}")
  protected int                     replicas;

  @Getter
  @Value("${analytics.es.shards:1}")
  protected int                     shards;

  @PostConstruct
  public void init() {
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

  public String getCreateIndexRequestContent() {
    return " {" +
        "\"aliases\": {" +
        "  \"" + indexAlias + "\": {" +
        "    \"is_write_index\" : true" +
        "  }" +
        "}" +
        "}";
  }

  public String getTurnOffWriteOnAllAnalyticsIndexes() {
    return "{" +
        "\"actions\": [" +
        "  {" +
        "    \"add\": {" +
        "      \"index\": \"" + indexPrefix + "*\"," +
        "      \"alias\": \"" + indexAlias + "\"," +
        "      \"is_write_index\": false" +
        "    }" +
        "  }" +
        "]" +
        "}";
  }

  public String getCreateDocumentRequestContent(String id) {
    JSONObject jsonObject = createCUDHeaderRequestContent(id);
    Document document = create(id);
    if (document == null) {
      return null;
    }
    JSONObject createRequest = new JSONObject();
    createRequest.put("create", jsonObject);
    return createRequest.toString(2) + "\n" + document.toJSON() + "\n";
  }

  private JSONObject createCUDHeaderRequestContent(String id) {
    JSONObject cudHeader = new JSONObject();
    cudHeader.put("_index", indexAlias);
    cudHeader.put("_id", id);
    return cudHeader;
  }

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

  public void storeCreatedIndexTemplate() {
    this.settingService.set(ES_ANALYTICS_CONTEXT,
                            ES_ANALYTICS_SCOPE,
                            ES_ANALYTICS_INDEX_TEMPLATE,
                            SettingValue.create(indexTemplate));
  }
}
