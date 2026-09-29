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
package io.meeds.analytics.elasticsearch.storage;

import static io.meeds.analytics.elasticsearch.listener.ElasticsearchMappingListener.FIELD_MAPPING_CREATED_EVENT;
import static io.meeds.analytics.utils.AnalyticsUtils.ALTERNATIVE_FIELD_SUFFIX;
import static io.meeds.analytics.utils.AnalyticsUtils.DEFAULT_FIELDS;
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
import static io.meeds.analytics.utils.AnalyticsUtils.MAX_ALTERNATIVE_FIELD_COUNT;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.ElasticsearchMappingConflictException;
import io.meeds.analytics.elasticsearch.model.ElasticsearchResponse;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.model.StatisticFieldMapping;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class ElasticsearchAnalyticsStorage {

  private static final String           TEXT_MAPPING_TYPE           = "text";

  private static final String           KEYWORD_MAPPING_TYPE        = "keyword";

  private static final String           BOOLEAN_MAPPING_TYPE        = "boolean";

  private static final String           FLOAT_MAPPING_TYPE          = "float";

  private static final String           LONG_MAPPING_TYPE           = "long";

  private static final String           VERSION_CONFLICT_ERROR_TYPE = "version_conflict_engine_exception";

  /**
   * Bulk item error types Elasticsearch answers when it cannot parse a
   * document against the mapping its index holds: {@code document_parsing_exception}
   * on the engine the platform runs (verified on 9.x); older releases
   * answered {@code mapper_parsing_exception}.
   * <p>
   * The type does not name the cause: a value that does not fit its field's
   * type and a refusal to add new fields past
   * {@code index.mapping.total_fields.limit} carry the same one (both
   * verified on 9.5.3), and only the reason text tells them apart — a text
   * this code deliberately does not match, since it is the part that moves
   * between engine versions. The caller therefore treats a refusal as a
   * possibly stale mapping view and re-reads it under a throttle, rather than
   * once per refused document.
   */
  private static final Set<String>      MAPPING_CONFLICT_ERROR_TYPES = Set.of("document_parsing_exception",
                                                                              "mapper_parsing_exception");

  private static final Set<String>      EXPLICIT_MAPPING_TYPES      = Set.of(KEYWORD_MAPPING_TYPE,
                                                                             TEXT_MAPPING_TYPE,
                                                                             LONG_MAPPING_TYPE,
                                                                             FLOAT_MAPPING_TYPE,
                                                                             BOOLEAN_MAPPING_TYPE);

  private static final Log              LOG                         =
                                            ExoLogger.getExoLogger(ElasticsearchAnalyticsStorage.class);

  private static final long             DAY_IN_MS                   = 86400000L;

  private static final String           DAY_DATE_FORMAT             = "yyyy-MM-dd";

  public static final DateTimeFormatter DAY_DATE_FORMATTER          = DateTimeFormatter.ofPattern(DAY_DATE_FORMAT)
                                                                                       .withResolverStyle(ResolverStyle.LENIENT);

  private Set<String>                   ignoredFieldNames           = ConcurrentHashMap.newKeySet();

  private Map<String, String>           mappedFieldNames            = new ConcurrentHashMap<>();

  @Autowired
  private ListenerService               listenerService;

  @Autowired
  private ElasticsearchConfiguration    elasticsearchConfiguration;

  @Autowired
  @Qualifier("elasticsearchHttpClient")
  private HttpClient                    httpClient;

  @PostConstruct
  public void init() {
    try {
      sendIndexTemplateRequest();
      CompletableFuture.runAsync(this::sendRolloverRequest);
    } catch (Exception e) {
      LOG.warn("Error while initializing Elasticsearch connection", e);
    }
  }

  /**
   * @return the configured weekly index name prefix
   *         ({@code analytics.es.index.prefix}), which the mapping reader
   *         needs to recognise the indices behind the alias
   */
  public String getIndexPrefix() {
    return elasticsearchConfiguration.getIndexPrefix();
  }

  public void sendCreateBulkDocumentsRequest(List<StatisticDataQueueEntry> dataQueueEntries,
                                             Set<StatisticFieldMapping> esMappings) {
    if (dataQueueEntries == null || dataQueueEntries.isEmpty()) {
      return;
    }

    LOG.debug("Indexing in bulk {} documents", dataQueueEntries.size());
    sendCreateIndexRequest(esMappings);

    StringBuilder request = new StringBuilder();
    for (StatisticDataQueueEntry statisticDataQueueEntry : dataQueueEntries) {
      String singleDocumentQuery = getCreateDocumentRequestContent(String.valueOf(statisticDataQueueEntry.getId()),
                                                                   statisticDataQueueEntry.getStatisticData(),
                                                                   esMappings);
      request.append(singleDocumentQuery);
    }

    LOG.debug("Create documents request to ES: {}", request);
    sendPutRequest("_bulk", request.toString());

    sendRefreshIndex();
  }

  public String search(String esQuery) {
    ElasticsearchResponse elasticResponse = sendPostRequest(elasticsearchConfiguration.getIndexAlias() + "/_search", esQuery);
    String response = elasticResponse.getMessage();
    int statusCode = elasticResponse.getStatusCode();
    if (StringUtils.isBlank(response)) {
      response = "Empty response was sent by ES";
    } else if (!isError(elasticResponse)) {
      org.json.JSONObject json = null;
      try {
        json = new JSONObject(response);
        if (json.has("status") && isError(json.getInt("status"))) {
          throw new IllegalStateException("Error occured while requesting ES HTTP error code: '" + statusCode +
              "', HTTP response: '" + response + "'");
        }
      } catch (JSONException e) {
        throw new IllegalStateException("Error occured while requesting ES HTTP code: '" + statusCode +
            "', Error parsing response to JSON format, content = '" + response + "'", e);
      }
    }
    return response;
  }

  public String retrieveAllAnalyticsIndexesMapping() {
    ElasticsearchResponse response = sendGetRequest(elasticsearchConfiguration.getIndexAlias() + "/_mapping", false);
    if (isError(response)) {
      LOG.warn("Error getting mapping of analytics : - \t\tcode : {} - \t\tmessage: {}",
               response.getStatusCode(),
               response.getMessage());
      return null;
    } else {
      return response.getMessage();
    }
  }

  public ElasticsearchResponse sendGetRequest(String uri) {
    return sendGetRequest(uri, true);
  }

  public ElasticsearchResponse sendGetRequest(String uri, boolean handleResponse) {
    ElasticsearchResponse response = sendHttpGetRequest(elasticsearchConfiguration.getUrlClient() + "/" + uri);
    if (handleResponse) {
      return handleESResponse(response, uri, null);
    } else {
      return response;
    }
  }

  public ElasticsearchResponse sendHeadRequest(String uri) {
    ElasticsearchResponse response = sendHttpHeadRequest(elasticsearchConfiguration.getUrlClient() + "/" + uri);
    return handleESResponse(response, uri, null);
  }

  public ElasticsearchResponse sendPutRequest(String uri, String content) {
    ElasticsearchResponse response = sendHttpPutRequest(elasticsearchConfiguration.getUrlClient() + "/" + uri, content);
    return handleESResponse(response, uri, content);
  }

  public ElasticsearchResponse sendDeleteRequest(String uri) {
    ElasticsearchResponse response = sendHttpDeleteRequest(elasticsearchConfiguration.getUrlClient() + "/" + uri);
    return handleESResponse(response, uri, null);
  }

  public ElasticsearchResponse sendPostRequest(String uri, String content) {
    ElasticsearchResponse response = sendHttpPostRequest(elasticsearchConfiguration.getUrlClient() + "/" + uri, content);
    return handleESResponse(response, uri, content);
  }

  private boolean sendCreateIndexRequest(Set<StatisticFieldMapping> esMappings) {
    String index = getIndex();
    if (sendIsIndexExistsRequest(index)) {
      LOG.debug("Index {} already exists. Index creation requests will not be sent.", index);
      return false;
    } else {
      sendTurnOffWriteOnAllAnalyticsIndexes();
      int knownFieldMappingsCount = sendCreateIndex(index, esMappings);
      if (sendIsIndexExistsRequest(index)) {
        LOG.info("New analytics index {} created with {} known field mappings.", index, knownFieldMappingsCount);
        return true;
      } else {
        throw new IllegalStateException("Error creating index " + index + " on elasticsearch");
      }
    }
  }

  private JSONObject getKnownFieldMappingProperties(Set<StatisticFieldMapping> esMappings) {
    try {
      return computeKnownFieldMappingProperties(esMappings);
    } catch (Exception e) {
      LOG.warn("Error computing known field mappings, the new analytics index will map its fields dynamically", e);
      return new JSONObject();
    }
  }

  private JSONObject computeKnownFieldMappingProperties(Set<StatisticFieldMapping> esMappings) {
    Map<String, StatisticFieldMapping> knownMappings = new HashMap<>();
    mappedFieldNames.forEach((name, type) -> knownMappings.put(name, new StatisticFieldMapping(name, type, false)));
    if (esMappings != null) {
      esMappings.stream()
                .filter(mapping -> !mapping.isScriptedField())
                .forEach(mapping -> knownMappings.put(mapping.getName(), mapping));
    }
    Set<String> templateFieldNames = getIndexTemplateFieldNames();
    JSONObject properties = new JSONObject();
    knownMappings.values()
                 .stream()
                 .filter(mapping -> !StringUtils.contains(mapping.getName(), '.'))
                 .filter(mapping -> !templateFieldNames.contains(mapping.getName()))
                 .filter(mapping -> mapping.getType() != null && EXPLICIT_MAPPING_TYPES.contains(mapping.getType()))
                 .forEach(mapping -> {
                   JSONObject property = new JSONObject().put("type", mapping.getType());
                   if (TEXT_MAPPING_TYPE.equals(mapping.getType()) && mapping.isHasKeywordSubField()) {
                     property.put("fields",
                                  new JSONObject().put(KEYWORD_MAPPING_TYPE,
                                                       new JSONObject().put("type", KEYWORD_MAPPING_TYPE)
                                                                       .put("ignore_above", 256)));
                   }
                   properties.put(mapping.getName(), property);
                 });
    return properties;
  }

  private Set<String> getIndexTemplateFieldNames() {
    try {
      JSONObject templateProperties = new JSONObject(elasticsearchConfiguration.getIndexTemplateMapping()).getJSONObject("template")
                                                                                                          .getJSONObject("mappings")
                                                                                                          .getJSONObject("properties");
      return new HashSet<>(templateProperties.keySet());
    } catch (Exception e) {
      LOG.debug("Error reading index template field names, default fields will be used", e);
      return new HashSet<>(DEFAULT_FIELDS);
    }
  }

  private void sendTurnOffWriteOnAllAnalyticsIndexes() {
    if (sendIsIndexExistsRequest(elasticsearchConfiguration.getIndexAlias())) {
      String esQuery = getTurnOffWriteOnAllAnalyticsIndexes();
      try {
        sendPostRequest("_aliases", esQuery);
        LOG.info("All analytics indexes switched to RO mode to prepare creation of a new index");
      } catch (Exception e) {
        LOG.warn("Analytics old indexes seems to not be turned off on write access");
      }
    }
  }

  @Cacheable("analytics.indexExists")
  private boolean sendIsIndexExistsRequest(String esIndex) {
    ElasticsearchResponse responseExists = sendGetRequest(esIndex, false);
    return responseExists.getStatusCode() == HttpStatus.SC_OK;
  }

  @CacheEvict("analytics.indexExists")
  private int sendCreateIndex(String index, Set<StatisticFieldMapping> esMappings) {
    JSONObject knownProperties = getKnownFieldMappingProperties(esMappings);
    int knownFieldMappingsCount = knownProperties.length();
    try {
      sendPutRequest(index, getCreateIndexRequestContent(knownProperties));
    } catch (RuntimeException e) {
      if (knownProperties.isEmpty() || sendGetRequest(index, false).getStatusCode() == HttpStatus.SC_OK) {
        throw e;
      }
      LOG.warn("Error creating analytics index {} with {} known field mappings, retrying without them, its fields will be mapped dynamically. Error: {}",
               index,
               knownProperties.length(),
               e.getMessage());
      sendPutRequest(index, getCreateIndexRequestContent(new JSONObject()));
      knownFieldMappingsCount = 0;
    }
    CompletableFuture.runAsync(this::sendRolloverRequest);
    return knownFieldMappingsCount;
  }

  private boolean sendIsIndexTemplateExistsRequest() {
    ElasticsearchResponse responseExists = sendGetRequest("_index_template/" + elasticsearchConfiguration.getIndexTemplateName(),
                                                          false);
    return responseExists.getStatusCode() == HttpStatus.SC_OK;
  }

  private void sendRefreshIndex() {
    sendRefreshIndex(elasticsearchConfiguration.getIndexAlias());
  }

  private void sendRefreshIndex(String index) {
    sendPostRequest(index + "/_refresh", null);
  }

  @SneakyThrows
  private ElasticsearchResponse sendHttpPostRequest(String url, String content) {
    HttpPost httpTypeRequest = new HttpPost(url);
    if (StringUtils.isNotBlank(content)) {
      httpTypeRequest.setEntity(new StringEntity(content, ContentType.APPLICATION_JSON));
    }
    return httpClient.execute(httpTypeRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  private ElasticsearchResponse sendHttpPutRequest(String url, String content) {
    HttpPut httpTypeRequest = new HttpPut(url);
    if (StringUtils.isNotBlank(content)) {
      httpTypeRequest.setEntity(new StringEntity(content, ContentType.APPLICATION_JSON));
    }
    return httpClient.execute(httpTypeRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  private ElasticsearchResponse sendHttpDeleteRequest(String url) {
    HttpDelete httpDeleteRequest = new HttpDelete(url);
    return httpClient.execute(httpDeleteRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  private ElasticsearchResponse sendHttpGetRequest(String url) {
    HttpGet httpGetRequest = new HttpGet(url);
    return httpClient.execute(httpGetRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  private ElasticsearchResponse sendHttpHeadRequest(String url) {
    HttpHead httpHeadRequest = new HttpHead(url);
    return httpClient.execute(httpHeadRequest, this::handleHttpResponse);
  }

  private String getCreateIndexRequestContent(JSONObject knownProperties) {
    JSONObject content = new JSONObject().put("aliases",
                                              new JSONObject().put(elasticsearchConfiguration.getIndexAlias(),
                                                                   new JSONObject().put("is_write_index", true)));
    if (knownProperties != null && !knownProperties.isEmpty()) {
      content.put("mappings", new JSONObject().put("properties", knownProperties));
    }
    return content.toString();
  }

  private String getTurnOffWriteOnAllAnalyticsIndexes() {
    return "{" +
        "\"actions\": [" +
        "  {" +
        "    \"add\": {" +
        "      \"index\": \"" + elasticsearchConfiguration.getIndexPrefix() + "*\"," +
        "      \"alias\": \"" + elasticsearchConfiguration.getIndexAlias() + "\"," +
        "      \"is_write_index\": false" +
        "    }" +
        "  }" +
        "]" +
        "}";
  }

  private String getCreateDocumentRequestContent(String id, // NOSONAR
                                                 StatisticData data,
                                                 Set<StatisticFieldMapping> esMappings) {
    JSONObject jsonObject = createCUDHeaderRequestContent(id);
    String timestampString = String.valueOf(data.getTimestamp());

    Map<String, String> fields = new HashMap<>();
    fields.put("id", id);
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
    Map<String, StatisticFieldMapping> mappedFields = esMappings.stream()
                                                                .collect(Collectors.toMap(StatisticFieldMapping::getName,
                                                                                          Function.identity()));
    if (MapUtils.isNotEmpty(data.getParameters())) {
      data.getParameters()
          .keySet()
          .stream()
          .filter(p -> getFieldMapping(mappedFields, p) == null)
          .forEach(f -> createFieldMapping(f, data.getParameters().get(f)));
      Map<String, String> parameters = data.getParameters()
                                           .entrySet()
                                           .stream()
                                           .filter(e -> e.getValue() != null && StringUtils.isNotBlank(e.getValue().toString()))
                                           .map(e -> {
                                             String name = e.getKey();
                                             Object value = e.getValue();
                                             if (value == null) {
                                               return null;
                                             }
                                             StatisticFieldMapping mapping = getFieldMapping(mappedFields, name);
                                             return normalizeFieldValueForMapping(mappedFields,
                                                                                  e,
                                                                                  mapping,
                                                                                  data.getParameters());
                                           })
                                           .filter(Objects::nonNull)
                                           .collect(Collectors.toMap(Entry::getKey, e -> getFieldValue(e.getValue())));
      fields.putAll(parameters);
    }
    Document document = new Document(String.valueOf(id),
                                     null,
                                     null,
                                     (Set<String>) null,
                                     fields);
    if (MapUtils.isNotEmpty(data.getListParameters())) {
      data.getListParameters()
          .keySet()
          .stream()
          .filter(p -> getFieldMapping(mappedFields, p) == null)
          .filter(p -> CollectionUtils.isNotEmpty(data.getListParameters().get(p)))
          .forEach(p -> createFieldMapping(p, data.getListParameters().get(p)));
      Map<String, Collection<String>> parameters = data.getListParameters()
                                                       .entrySet()
                                                       .stream()
                                                       .filter(e -> CollectionUtils.isNotEmpty(e.getValue()))
                                                       .map(e -> {
                                                         String name = e.getKey();
                                                         StatisticFieldMapping mapping = getFieldMapping(mappedFields, name);
                                                         return normalizeFieldValueCollectionForMapping(mappedFields,
                                                                                                        e,
                                                                                                        mapping);
                                                       })
                                                       .filter(Objects::nonNull)
                                                       .collect(Collectors.toMap(Entry::getKey,
                                                                                 e -> getFieldValue(e.getValue())));
      document.setListFields(parameters);
    }
    JSONObject createRequest = new JSONObject();
    createRequest.put("create", jsonObject);
    return createRequest.toString() + "\n" + document.toJSON() + "\n";
  }

  private Entry<String, ? extends Object> normalizeFieldValueForMapping(Map<String, StatisticFieldMapping> mappedFields,
                                                                        Entry<String, Object> pair,
                                                                        StatisticFieldMapping originalMapping,
                                                                        Map<String, Object> parameters) {
    String name = pair.getKey();
    Object value = pair.getValue();
    StatisticFieldMapping latestAltMapping = getLatestAlternativeFieldMapping(mappedFields, name);
    if (latestAltMapping != null) {
      return normalizeAlternativeFieldValueForMapping(name, value, latestAltMapping, parameters);
    } else if (checkFieldMapping(value, originalMapping)) {
      return normalizeFieldValueForMapping(pair, originalMapping);
    } else {
      return createAlternativeFieldValueForMapping(originalMapping, name, value, 1);
    }
  }

  private Entry<String, ? extends Collection<Object>> normalizeFieldValueCollectionForMapping(Map<String, StatisticFieldMapping> mappedFields,
                                                                                              Entry<String, Collection<Object>> pair,
                                                                                              StatisticFieldMapping originalMapping) {
    String name = pair.getKey();
    Collection<Object> value = pair.getValue();
    StatisticFieldMapping latestAltMapping = getLatestAlternativeFieldMapping(mappedFields, name);
    if (latestAltMapping != null) {
      return normalizeAlternativeFieldValueCollectionForMapping(name, value, latestAltMapping);
    } else if (checkFieldMapping(value, originalMapping)) {
      return normalizeFieldValueCollectionForMapping(pair, originalMapping);
    } else {
      return createAlternativeFieldValueCollectionForMapping(latestAltMapping, name, value, 1);
    }
  }

  private Entry<String, ? extends Object> normalizeAlternativeFieldValueForMapping(String name,
                                                                                   Object value,
                                                                                   StatisticFieldMapping latestAltMapping,
                                                                                   Map<String, Object> parameters) {
    if (checkFieldMapping(value, latestAltMapping)) {
      return normalizeFieldValueForMapping(Pair.of(latestAltMapping.getName(), value), latestAltMapping);
    }
    int nextAlternativeIndex = getAlternativeFieldIndex(latestAltMapping.getName()) + 1;
    if (nextAlternativeIndex <= MAX_ALTERNATIVE_FIELD_COUNT) {
      return createAlternativeFieldValueForMapping(latestAltMapping, name, value, nextAlternativeIndex);
    }
    if (ignoredFieldNames.add(name)) {
      LOG.warn("Field with name '{}' and type '{}' isn't compatible with latest ES alternative field '{}' of type '{}'. All alternative fields are unavailable. Ignore adding it in indexed document {}",
               name,
               getFieldMappingType(value),
               latestAltMapping.getName(),
               latestAltMapping.getType(),
               parameters);
    }
    return null;
  }

  private Entry<String, ? extends Collection<Object>> normalizeAlternativeFieldValueCollectionForMapping(String name,
                                                                                                         Collection<Object> value,
                                                                                                         StatisticFieldMapping latestAltMapping) {
    if (checkFieldMapping(value, latestAltMapping)) {
      return normalizeFieldValueCollectionForMapping(Pair.of(latestAltMapping.getName(), value), latestAltMapping);
    }
    int nextAlternativeIndex = getAlternativeFieldIndex(latestAltMapping.getName()) + 1;
    if (nextAlternativeIndex <= MAX_ALTERNATIVE_FIELD_COUNT) {
      return createAlternativeFieldValueCollectionForMapping(latestAltMapping, name, value, nextAlternativeIndex);
    }
    if (ignoredFieldNames.add(name)) {
      LOG.warn("Field with name '{}' and type '{}' isn't compatible with latest ES alternative field '{}' of type '{}'. All alternative fields are unavailable. Ignore adding it in indexed document.",
               name,
               getFieldMappingType(value),
               latestAltMapping.getName(),
               latestAltMapping.getType());
    }
    return null;
  }

  private Entry<String, ? extends Object> createAlternativeFieldValueForMapping(StatisticFieldMapping existingMapping,
                                                                                String name,
                                                                                Object value,
                                                                                int alternativeIndex) {
    String altFieldName = getAlternativeFieldName(name, alternativeIndex);
    String altFieldType = createFieldMapping(altFieldName, value);
    LOG.warn("ES Field '{}' will be renamed to '{}' due to different type: ES Type = '{}', detected type = '{}' (value = '{}')",
             existingMapping.getName(),
             altFieldName,
             existingMapping.getType(),
             altFieldType,
             value);
    StatisticFieldMapping createdAltMapping = new StatisticFieldMapping(altFieldName, altFieldType, false);
    return normalizeFieldValueForMapping(Pair.of(altFieldName, value), createdAltMapping);
  }

  private Entry<String, ? extends Collection<Object>> createAlternativeFieldValueCollectionForMapping(StatisticFieldMapping existingMapping,
                                                                                                      String name,
                                                                                                      Collection<Object> value,
                                                                                                      int alternativeIndex) {
    String altFieldName = getAlternativeFieldName(name, alternativeIndex);
    String altFieldType = createFieldMapping(altFieldName, value);
    LOG.warn("ES Field '{}' will be renamed to '{}' due to different type: ES Type = '{}', detected type = '{}' (list of value = '{}')",
             existingMapping.getName(),
             altFieldName,
             existingMapping.getType(),
             altFieldType,
             StringUtils.join(value, ","));
    StatisticFieldMapping createdAltMapping = new StatisticFieldMapping(altFieldName, altFieldType, false);
    return normalizeFieldValueCollectionForMapping(Pair.of(altFieldName, value), createdAltMapping);
  }

  private StatisticFieldMapping getLatestAlternativeFieldMapping(Map<String, StatisticFieldMapping> mappedFields, String name) {
    StatisticFieldMapping latestAltMapping = null;
    for (int i = 1; i <= MAX_ALTERNATIVE_FIELD_COUNT; i++) {
      StatisticFieldMapping altMapping = getFieldMapping(mappedFields, getAlternativeFieldName(name, i));
      if (altMapping != null) {
        latestAltMapping = altMapping;
      }
    }
    return latestAltMapping;
  }

  private int getAlternativeFieldIndex(String name) {
    if (!StringUtils.contains(name, ALTERNATIVE_FIELD_SUFFIX)) {
      return 0;
    }
    String index = StringUtils.substringAfterLast(name, ALTERNATIVE_FIELD_SUFFIX);
    return StringUtils.isBlank(index) ? 1 : Integer.parseInt(index);
  }

  private String getAlternativeFieldName(String name, int alternativeIndex) {
    String baseName = getBaseFieldName(name);
    return alternativeIndex == 1 ? "%s%s".formatted(baseName, ALTERNATIVE_FIELD_SUFFIX) :
                                 "%s%s%s".formatted(baseName, ALTERNATIVE_FIELD_SUFFIX, alternativeIndex);
  }

  private String getBaseFieldName(String name) {
    return name == null ? null : name.replaceFirst("%s\\d*$".formatted(ALTERNATIVE_FIELD_SUFFIX), "");
  }

  private Entry<String, ? extends Object> normalizeFieldValueForMapping(Entry<String, Object> pair,
                                                                        StatisticFieldMapping mapping) {
    if (mapping != null
        && (mapping.getType().equals(KEYWORD_MAPPING_TYPE)
            || mapping.getType().equals(TEXT_MAPPING_TYPE))
        && !(pair.getValue() instanceof String)) {
      return Pair.of(pair.getKey(), pair.getValue().toString());
    } else {
      return pair;
    }
  }

  private Entry<String, ? extends Collection<Object>> normalizeFieldValueCollectionForMapping(Entry<String, Collection<Object>> pair,
                                                                                              StatisticFieldMapping mapping) {
    if (mapping != null
        && (mapping.getType().equals(KEYWORD_MAPPING_TYPE)
            || mapping.getType().equals(TEXT_MAPPING_TYPE))
        && pair.getValue().stream().anyMatch(v -> !(v instanceof String))) {
      return Pair.of(pair.getKey(),
                     pair.getValue()
                         .stream()
                         .filter(Objects::nonNull)
                         .map(Object::toString)
                         .map(Object.class::cast)
                         .toList());
    } else {
      return pair;
    }
  }

  private StatisticFieldMapping getFieldMapping(Map<String, StatisticFieldMapping> mappedFields, String name) {
    if (mappedFields.containsKey(name)) {
      return mappedFields.get(name);
    } else if (mappedFieldNames.containsKey(name)) {
      return new StatisticFieldMapping(name, mappedFieldNames.get(name), false);
    } else {
      return null;
    }
  }

  private String createFieldMapping(String f, Object value) {
    String type = getFieldMappingType(value);
    String existingType = mappedFieldNames.putIfAbsent(f, type);
    if (existingType == null) {
      try {
        sendPutRequest(elasticsearchConfiguration.getIndexAlias() + "/_mapping", String.format("""
            {
              "properties": {
                "%s" : {
                  "type" : "%s"
                }
              }
            }
            """, f, type));
        LOG.info("Create ES Mapping for field '{}' with type '{}'", f, type);
        sendRefreshIndex();
        listenerService.broadcast(FIELD_MAPPING_CREATED_EVENT, f, type);
      } catch (Exception e) {
        if (LOG.isDebugEnabled()) {
          LOG.warn("Error while creating ES Mapping for field '{}' with type '{}'. It may already exists. Continue and consider it as existing.",
                   f,
                   type,
                   e);
        } else {
          LOG.warn("Error while creating ES Mapping for field '{}' with type '{}'. It may already exists. Continue and consider it as existing. Error: {}",
                   f,
                   type,
                   e.getMessage());
        }
      }
      return type;
    } else {
      return existingType;
    }
  }

  private boolean checkFieldMapping(Object value, StatisticFieldMapping mapping) { // NOSONAR
    if (mapping == null) {
      return true;
    } else {
      String fieldMappingType = getFieldMappingType(value);
      String mappedType = mapping.getType();
      if (StringUtils.equalsIgnoreCase(mappedType, fieldMappingType)) {
        return true;
      } else {
        return switch (mappedType) {
        case LONG_MAPPING_TYPE -> LONG_MAPPING_TYPE.equals(fieldMappingType)
                                  || (KEYWORD_MAPPING_TYPE.equals(fieldMappingType)
                                      && value instanceof String s
                                      && isLongValue(s));
        case FLOAT_MAPPING_TYPE -> FLOAT_MAPPING_TYPE.equals(fieldMappingType)
                                   || LONG_MAPPING_TYPE.equals(fieldMappingType)
                                   || (KEYWORD_MAPPING_TYPE.equals(fieldMappingType)
                                       && value instanceof String s
                                       && isDecimalValue(s));
        case BOOLEAN_MAPPING_TYPE -> BOOLEAN_MAPPING_TYPE.equals(fieldMappingType)
                                     || (KEYWORD_MAPPING_TYPE.equals(fieldMappingType)
                                         && value instanceof String s
                                         && isBooleanValue(s));
        case KEYWORD_MAPPING_TYPE, TEXT_MAPPING_TYPE -> KEYWORD_MAPPING_TYPE.equals(fieldMappingType)
                                                        || TEXT_MAPPING_TYPE.equals(fieldMappingType);
        default -> false;
        };
      }
    }
  }

  private boolean isBooleanValue(String stringValue) {
    return StringUtils.equalsAnyIgnoreCase(stringValue, "true", "false");
  }

  private boolean isLongValue(String value) {
    try {
      Long.parseLong(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private boolean isDecimalValue(String value) {
    try {
      new BigDecimal(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @SuppressWarnings("rawtypes")
  private String getFieldMappingType(Object value) {
    return switch (value) {
    case Integer v -> LONG_MAPPING_TYPE;
    case Short v -> LONG_MAPPING_TYPE;
    case Long v -> LONG_MAPPING_TYPE;
    case Byte v -> LONG_MAPPING_TYPE;
    case Float v -> FLOAT_MAPPING_TYPE;
    case Double v -> FLOAT_MAPPING_TYPE;
    case Boolean v -> BOOLEAN_MAPPING_TYPE;
    case Collection v -> ((Collection<?>) v).stream()
                                            .filter(Objects::nonNull)
                                            .findFirst()
                                            .map(this::getFieldMappingType)
                                            .orElse(KEYWORD_MAPPING_TYPE);
    default -> KEYWORD_MAPPING_TYPE;
    };
  }

  private String getFieldValue(Object value) {
    return switch (value) {
    case Integer v -> BigDecimal.valueOf(v).toPlainString();
    case Long v -> BigDecimal.valueOf(v).toPlainString();
    case Short v -> BigDecimal.valueOf(v).toPlainString();
    case Byte v -> BigDecimal.valueOf(v).toPlainString();
    case Float v -> BigDecimal.valueOf(v).toPlainString();
    case Double v -> BigDecimal.valueOf(v).toPlainString();
    default -> String.valueOf(value);
    };
  }

  private Collection<String> getFieldValue(Collection<Object> value) {
    return value.stream().map(this::getFieldValue).toList();
  }

  private JSONObject createCUDHeaderRequestContent(String id) {
    JSONObject cudHeader = new JSONObject();
    cudHeader.put("_index", elasticsearchConfiguration.getIndexAlias());
    cudHeader.put("_id", id);
    return cudHeader;
  }

  /**
   * Handle Http response receive from ES Log an INFO if the return status code
   * is 2xx Log an ERROR if the return code is different from 2xx
   *
   * @param httpResponse The Http Response to handle
   */
  @SneakyThrows
  private ElasticsearchResponse handleHttpResponse(ClassicHttpResponse httpResponse) throws IOException {
    final HttpEntity entity = httpResponse.getEntity();
    int statusCode = httpResponse.getCode();
    return new ElasticsearchResponse(EntityUtils.toString(entity), statusCode);
  }

  private boolean isError(ElasticsearchResponse response) {
    return isError(response.getStatusCode());
  }

  private boolean isError(int status) {
    return status / 100 != 2;
  }

  private ElasticsearchResponse handleESResponse(ElasticsearchResponse response, String uri, String content) {
    if (isError(response)) {
      throw new IllegalStateException(String.format("Error message returned from ES: %s. URI: %s. Content: %s",
                                                    response.getMessage(),
                                                    uri,
                                                    content));
    }
    if (StringUtils.contains(response.getMessage(), "\"errors\":true")) {
      List<BulkItemError> errors = getBulkItemErrors(response.getMessage());
      if (!errors.isEmpty() && errors.stream().allMatch(error -> VERSION_CONFLICT_ERROR_TYPE.equals(error.type()))) {
        // A "create" refused for an existing id means the entry is already
        // indexed: a retried bulk, or a queue entry replayed by another node.
        // Nothing is lost, so this is a designed outcome, not an error.
        LOG.debug("{} document(s) already indexed, ignored: {}",
                  errors.size(),
                  errors.stream().map(BulkItemError::id).toList());
      } else {
        String message = String.format("Error message returned from ES: %s. URI: %s. Content: %s",
                                       response.getMessage(),
                                       uri,
                                       content);
        List<BulkItemError> conflicts = errors.stream()
                                              .filter(error -> MAPPING_CONFLICT_ERROR_TYPES.contains(error.type()))
                                              .toList();
        // A mapping conflict is only reported as such when nothing else
        // failed: the caller retries the refused documents and returns, so an
        // item refused for another reason (a 429 under shard pressure, a
        // 503) in the same bulk would be marked processed and lost. With
        // another error present the bulk is a generic failure, and the
        // dispatcher's one-by-one fallback lets each document meet its own
        // outcome, the conflict path included.
        boolean onlyConflictsOrDuplicates = errors.stream()
                                                  .allMatch(error -> MAPPING_CONFLICT_ERROR_TYPES.contains(error.type())
                                                                     || VERSION_CONFLICT_ERROR_TYPE.equals(error.type()));
        if (!conflicts.isEmpty() && onlyConflictsOrDuplicates) {
          Set<String> refusedIds = conflicts.stream()
                                            .map(BulkItemError::id)
                                            .filter(StringUtils::isNotBlank)
                                            .collect(Collectors.toSet());
          if (refusedIds.size() != conflicts.size()) {
            // A refused item without an id cannot be matched to its entry:
            // carry no id at all, so the caller retries the whole batch
            // rather than the identified documents only.
            refusedIds = Set.of();
          }
          throw new ElasticsearchMappingConflictException(message,
                                                          refusedIds,
                                                          conflicts.stream().map(BulkItemError::reason).toList());
        } else {
          throw new IllegalStateException(message);
        }
      }
    }
    return response;
  }

  /** One failed item of a {@code _bulk} response */
  private record BulkItemError(String type, String id, String reason) {
  }

  /**
   * @param bulkResponse the body of a {@code _bulk} response with
   *          {@code "errors":true}
   * @return the failed items, empty when the body cannot be parsed so that
   *         the caller falls back to a generic error
   */
  private List<BulkItemError> getBulkItemErrors(String bulkResponse) {
    List<BulkItemError> errors = new ArrayList<>();
    try {
      JSONArray items = new JSONObject(bulkResponse).optJSONArray("items");
      for (int i = 0; items != null && i < items.length(); i++) {
        JSONObject item = items.getJSONObject(i);
        for (String action : item.keySet()) {
          JSONObject result = item.getJSONObject(action);
          JSONObject error = result.optJSONObject("error");
          if (error != null) {
            // Never null: the ids and reasons are collected into Set.copyOf /
            // List.copyOf, which refuse a null element.
            errors.add(new BulkItemError(error.optString("type", "unknown"),
                                         result.optString("_id", ""),
                                         error.optString("reason", "")));
          }
        }
      }
    } catch (JSONException e) {
      LOG.debug("Error parsing ES bulk response, it will be handled as a generic error: {}", bulkResponse, e);
    }
    return errors;
  }

  /**
   * Pushes the index template at every startup, not only when it is missing.
   * The template is versioned with the product ({@code numeric_detection} was
   * switched off in MEED-9542) but a cluster provisioned before a change kept
   * the template it was created with, and every new weekly index inherited
   * it (EXO-90504). The request is idempotent; a refused update on an
   * existing template is logged and does not fail the startup.
   */
  private void sendIndexTemplateRequest() {
    String indexTemplate = elasticsearchConfiguration.getIndexTemplateName();
    boolean exists = sendIsIndexTemplateExistsRequest();
    try {
      sendPutRequest("_index_template/" + indexTemplate, elasticsearchConfiguration.getIndexTemplateMapping());
    } catch (RuntimeException e) {
      if (exists) {
        LOG.warn("Error updating Index Template {}, the one already deployed on the cluster is kept. Error: {}",
                 indexTemplate,
                 e.getMessage());
        return;
      }
      throw e;
    }
    if (exists) {
      // One line per startup, on purpose: an administrator who edited the
      // template on the cluster gets the signal that the product re-applied
      // its own; the supported customisation is analytics.es.index.template.path.
      LOG.info("Index Template {} re-applied from the product's definition.", indexTemplate);
    } else if (sendIsIndexTemplateExistsRequest()) {
      LOG.info("Index Template {} created.", indexTemplate);
    } else {
      throw new IllegalStateException("Error while creating Index Template " + indexTemplate);
    }
  }

  private void sendRolloverRequest() {
    LOG.info("Analytics Indices rollover process start");
    ElasticsearchResponse response = sendGetRequest(elasticsearchConfiguration.getIndexPrefix() +
        "_*?allow_no_indices=true&ignore_unavailable=true");
    String indexListJsonString = response.getMessage();
    JSONObject jsonObject = new JSONObject(indexListJsonString);
    List<String> outdatedIndices = jsonObject.keySet()
                                             .stream()
                                             .sorted((s1, s2) -> StringUtils.compare(s2, s1))
                                             .skip(elasticsearchConfiguration.getMaxIndexCount())
                                             .filter(Objects::nonNull)
                                             .toList();
    while (!outdatedIndices.isEmpty()) {
      List<String> outdatedIndicesSubList = outdatedIndices.stream().limit(10).toList();
      String outdatedIndiceNames = StringUtils.join(outdatedIndicesSubList, ",");
      LOG.info("Deleting {} outdated analytics Indices: [{}]", outdatedIndicesSubList.size(), outdatedIndiceNames);
      sendDeleteRequest(outdatedIndiceNames);
      outdatedIndices = outdatedIndices.stream().skip(10).toList();
    }
    LOG.info("Analytics Indices rollover process finished successfully.");
  }

  private final String getIndex() {
    return getIndex(System.currentTimeMillis() / getIndexPerDaysMs());
  }

  @Cacheable("analytics.indexName")
  private final String getIndex(long indexPeriodIndex) {
    long periodEpochMs = indexPeriodIndex * getIndexPerDaysMs();
    String indexSuffix = DAY_DATE_FORMATTER.format(Instant.ofEpochMilli(periodEpochMs)
                                                          .atZone(ZoneOffset.UTC));
    return elasticsearchConfiguration.getIndexPrefix() + "_" + indexSuffix;
  }

  private long getIndexPerDaysMs() {
    return DAY_IN_MS * Math.max(elasticsearchConfiguration.getIndexPerDays(), 1);
  }

}
