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
package io.meeds.analytics.es;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.json.JSONException;

import org.exoplatform.commons.search.es.client.ElasticClient;
import org.exoplatform.commons.search.es.client.ElasticClientException;
import org.exoplatform.commons.search.es.client.ElasticIndexingAuditTrail;
import org.exoplatform.commons.search.es.client.ElasticResponse;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.model.StatisticDataQueueEntry;

public class AnalyticsESClient extends ElasticClient {

  private static final Log                      LOG                            =
                                                    ExoLogger.getExoLogger(AnalyticsESClient.class);

  private static final String                   DEFAULT_ES_CLIENT_SERVER_URL   = "http://127.0.0.1:9200";

  private static final String                   ES_CLIENT_SERVER_URL           = "exo.es.index.server.url";

  private static final String                   ES_CLIENT_USERNAME             = "exo.es.index.server.username";

  private static final String                   ES_CLIENT_PWD                  = "exo.es.index.server.password";                            // NOSONAR

  private static final String                   ES_ANALYTICS_CLIENT_SERVER_URL = "exo.es.analytics.index.server.url";

  private static final String                   ES_ANALYTICS_CLIENT_USERNAME   = "exo.es.analytics.index.server.username";

  private static final String                   ES_ANALYTICS_CLIENT_PWD        = "exo.es.analytics.index.server.password";                  // NOSONAR

  private static final String                   INDEX_TEMPLATE_FILE_PATH_PARAM = "index.template.file.path";

  private static final String                   ES_ANALYTICS_INDEX_PER_DAYS    = "exo.es.analytics.index.per.days";

  private static final long                     DAY_IN_MS                      = 86400000L;

  private static final String                   DAY_DATE_FORMAT                = "yyyy-MM-dd";

  public static final DateTimeFormatter         DAY_DATE_FORMATTER             = DateTimeFormatter.ofPattern(DAY_DATE_FORMAT)
                                                                                                  .withResolverStyle(ResolverStyle.LENIENT);

  private AnalyticsIndexingServiceConnector     analyticsIndexingConnector;

  private AnalyticsElasticContentRequestBuilder elasticContentRequestBuilder;

  private Set<String>                           existingIndexes                = new HashSet<>();

  private Map<Long, String>                     indexSuffixPerDayIndice        = new HashMap<>();

  private int                                   indexPerDays;

  private String                                esIndexTemplateQuery;

  private String                                username;

  private String                                password;

  public AnalyticsESClient(ConfigurationManager configurationManager, // NOSONAR
                           AnalyticsElasticContentRequestBuilder elasticContentRequestBuilder,
                           AnalyticsIndexingServiceConnector analyticsIndexingConnector,
                           ElasticIndexingAuditTrail auditTrail,
                           InitParams initParams) {
    super(auditTrail);
    this.analyticsIndexingConnector = analyticsIndexingConnector;
    this.elasticContentRequestBuilder = elasticContentRequestBuilder;

    if (initParams != null) {
      if (initParams.containsKey(ES_ANALYTICS_CLIENT_SERVER_URL)) {
        this.urlClient = initParams.getValueParam(ES_ANALYTICS_CLIENT_SERVER_URL).getValue();
      }
      if (initParams.containsKey(ES_ANALYTICS_CLIENT_USERNAME)) {
        this.username = initParams.getValueParam(ES_ANALYTICS_CLIENT_USERNAME).getValue();
      }
      if (initParams.containsKey(ES_ANALYTICS_CLIENT_PWD)) {
        this.password = initParams.getValueParam(ES_ANALYTICS_CLIENT_PWD).getValue();
      }
      if (initParams.containsKey(INDEX_TEMPLATE_FILE_PATH_PARAM)) {
        String mappingFilePath = initParams.getValueParam(INDEX_TEMPLATE_FILE_PATH_PARAM).getValue();
        try {
          this.esIndexTemplateQuery = getFileContent(configurationManager, mappingFilePath);
        } catch (Exception e) {
          LOG.error("Can't read elasticsearch index mapping from path {}", mappingFilePath, e);
        }
      }
      if (initParams.containsKey(ES_ANALYTICS_INDEX_PER_DAYS)) {
        this.indexPerDays = Integer.parseInt(initParams.getValueParam(ES_ANALYTICS_INDEX_PER_DAYS).getValue());
      }
    }
    if (StringUtils.isBlank(this.urlClient)) {
      this.urlClient = System.getProperty(ES_CLIENT_SERVER_URL);
      this.username = System.getProperty(ES_CLIENT_USERNAME);
      this.password = System.getProperty(ES_CLIENT_PWD);
    }
    if (StringUtils.isBlank(this.urlClient)) {
      this.urlClient = DEFAULT_ES_CLIENT_SERVER_URL;
    }
    if (StringUtils.isBlank(this.esIndexTemplateQuery)) {
      LOG.error("Empty elasticsearch index mapping file path parameter");
    }
    initHttpClient();
  }

  public void init() {
    checkIndexTemplateExistence();
    LOG.info("Analytics client initialized and is ready to proceed analytics data");
  }

  public boolean sendCreateIndexRequest(String index) {
    if (sendIsIndexExistsRequest(index)) {
      LOG.debug("Index {} already exists. Index creation requests will not be sent.", index);
      return false;
    } else {
      sendTurnOffWriteOnAllAnalyticsIndexes();

      String esIndexSettings = elasticContentRequestBuilder.getCreateIndexRequestContent(analyticsIndexingConnector);
      ElasticResponse createIndexResponse = sendHttpPutRequest(index, esIndexSettings);

      if (sendIsIndexExistsRequest(index)) {
        LOG.info("New analytics index {} created.", index);
        return true;
      } else {
        throw new IllegalStateException("Error creating index " + index + " on elasticsearch, response code = "
            + createIndexResponse.getStatusCode() + " , response content : "
            + createIndexResponse.getMessage());
      }
    }
  }

  public void sendTurnOffWriteOnAllAnalyticsIndexes() {
    if (sendIsIndexExistsRequest(analyticsIndexingConnector.getIndexAlias())) {
      String esQuery = elasticContentRequestBuilder.getTurnOffWriteOnAllAnalyticsIndexes(analyticsIndexingConnector);
      try {
        sendHttpPostRequest("_aliases", esQuery);
        LOG.info("All analytics indexes switched to RO mode to prepare creation of a new index");
      } catch (ElasticClientException e) {
        LOG.warn("Analytics old indexes seems to not be turned off on write access");
      }
    }
  }

  public boolean sendIsIndexExistsRequest(String esIndex) {
    if (existingIndexes.contains(esIndex)) {
      return true;
    }
    String url = urlClient + "/" + esIndex;
    ElasticResponse responseExists = super.sendHttpGetRequest(url);
    boolean indexExists = responseExists.getStatusCode() == HttpStatus.SC_OK;
    if (indexExists) {
      existingIndexes.add(esIndex);
    }
    return indexExists;
  }

  public boolean sendIsIndexTemplateExistsRequest() {
    String url = urlClient + "/_index_template/" + analyticsIndexingConnector.getIndexTemplate();
    ElasticResponse responseExists = super.sendHttpGetRequest(url);
    return responseExists.getStatusCode() == HttpStatus.SC_OK;
  }

  public void sendCreateBulkDocumentsRequest(List<StatisticDataQueueEntry> dataQueueEntries) {
    if (dataQueueEntries == null || dataQueueEntries.isEmpty()) {
      return;
    }

    LOG.info("Indexing in bulk {} documents", dataQueueEntries.size());

    checkIndexExistence(dataQueueEntries);

    StringBuilder request = new StringBuilder();
    for (StatisticDataQueueEntry statisticDataQueueEntry : dataQueueEntries) {
      String documentId = String.valueOf(statisticDataQueueEntry.getId());
      String singleDocumentQuery = elasticContentRequestBuilder.getCreateDocumentRequestContent(analyticsIndexingConnector,
                                                                                                documentId);
      request.append(singleDocumentQuery);
    }

    LOG.debug("Create documents request to ES: {}", request);
    sendHttpPutRequest("_bulk", request.toString());

    refreshIndex();
  }

  public String sendRequest(String esQuery) {
    ElasticResponse elasticResponse = sendHttpPostRequest(analyticsIndexingConnector.getIndexAlias() + "/_search", esQuery);
    String response = elasticResponse.getMessage();
    int statusCode = elasticResponse.getStatusCode();
    if (ElasticIndexingAuditTrail.isError(statusCode) || StringUtils.isBlank(response)) {
      if (StringUtils.isBlank(response)) {
        response = "Empty response was sent by ES";
      }
    } else {
      org.json.JSONObject json = null;
      try {
        json = new org.json.JSONObject(response);
        if (json.has("status") && ElasticIndexingAuditTrail.isError(json.getInt("status"))) {
          throw new IllegalStateException("Error occured while requesting ES HTTP error code: '" + statusCode
              + "', HTTP response: '"
              + response + "'");
        }
      } catch (JSONException e) {
        throw new IllegalStateException("Error occured while requesting ES HTTP code: '" + statusCode
            + "', Error parsing response to JSON format, content = '" + response + "'", e);
      }
    }
    return response;
  }

  public String retrieveAllAnalyticsIndexesMapping() {
    String url = urlClient + "/" + analyticsIndexingConnector.getIndexAlias() + "/_mapping";
    ElasticResponse response = super.sendHttpGetRequest(url);
    if (ElasticIndexingAuditTrail.isError(response.getStatusCode())) {
      LOG.warn("Error getting mapping of analytics : - \t\tcode : {} - \t\tmessage: {}",
               response.getStatusCode(),
               response.getMessage());
      return null;
    } else {
      return response.getMessage();
    }
  }

  @Override
  protected ElasticResponse sendHttpHeadRequest(String uri) {
    String url = urlClient + "/" + uri;
    ElasticResponse response = super.sendHttpHeadRequest(url);
    try {
      handleESResponse(response);
    } catch (Exception e) {
      throw new ElasticClientException("Error sending HEAD request '" + uri + "'", e);
    }
    return response;
  }

  @Override
  public ElasticResponse sendHttpGetRequest(String uri) {
    return sendHttpGetRequest(null, uri);
  }

  public ElasticResponse sendHttpGetRequest(String urlClient, String uri) {
    if (StringUtils.isBlank(urlClient)) {
      urlClient = this.urlClient;
    }
    String url = urlClient + "/" + uri;
    ElasticResponse response = super.sendHttpGetRequest(url);
    try {
      handleESResponse(response);
    } catch (Exception e) {
      throw new ElasticClientException("Error sending GET request '" + url + "'", e);
    }
    return response;
  }

  @Override
  public ElasticResponse sendHttpPutRequest(String uri, String content) {
    String url = urlClient + "/" + uri;
    ElasticResponse response = super.sendHttpPutRequest(url, content);
    try {
      handleESResponse(response);
    } catch (Exception e) {
      throw new ElasticClientException("Error sending PUT request '" + url + "' with content = '" + content + "'", e);
    }
    return response;
  }

  @Override
  public ElasticResponse sendHttpDeleteRequest(String uri) {
    String url = urlClient + "/" + uri;
    ElasticResponse response = super.sendHttpDeleteRequest(url);
    try {
      handleESResponse(response);
    } catch (Exception e) {
      throw new ElasticClientException("Error sending 'DELETE' request '" + url + "'", e);
    }
    return response;
  }

  @Override
  public ElasticResponse sendHttpPostRequest(String uri, String content) {
    String url = urlClient + "/" + uri;
    ElasticResponse response = super.sendHttpPostRequest(url, content);
    try {
      handleESResponse(response);
    } catch (Exception e) {
      throw new ElasticClientException("Error sending POST request '" + url + "' with content = '" + content + "'", e);
    }
    return response;
  }

  public String getIndexSuffix(long timestamp) {
    long indexSuffixLong = timestamp / (DAY_IN_MS * indexPerDays);
    String indexSuffix = indexSuffixPerDayIndice.get(indexSuffixLong);
    if (indexSuffix != null) {
      return indexSuffix;
    }
    indexSuffix = DAY_DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC));
    indexSuffixPerDayIndice.put(indexSuffixLong, indexSuffix);
    return indexSuffix;
  }

  public void refreshIndex() {
    refreshIndex(analyticsIndexingConnector.getIndexAlias());
  }

  public void refreshIndex(String index) {
    sendHttpPostRequest(index + "/_refresh", null);
  }

  public int getIndexPerDays() {
    return indexPerDays;
  }

  @Override
  protected String getEsUsernameProperty() {
    return username;
  }

  @Override
  protected String getEsPasswordProperty() {
    return password;
  }

  @Override
  protected HttpClientConnectionManager getClientConnectionManager() {
    return new PoolingHttpClientConnectionManager();
  }

  private void handleESResponse(ElasticResponse response) {
    if (response.getStatusCode() != 200) {
      throw new ElasticClientException(response.getMessage());
    }
    if (StringUtils.contains(response.getMessage(), "\"type\":\"version_conflict_engine_exception\"")) {
      LOG.warn("ID conflict in some content", response.getMessage());
      return;
    }
    if (response.getStatusCode() != 200 || StringUtils.contains(response.getMessage(), "\"errors\":true")) {
      throw new ElasticClientException(response.getMessage());
    }
  }

  private void checkIndexExistence(List<StatisticDataQueueEntry> dataQueueEntries) {
    Set<String> indexes = new HashSet<>();
    for (StatisticDataQueueEntry statisticDataQueueEntry : dataQueueEntries) {
      long timestamp = statisticDataQueueEntry.getStatisticData().getTimestamp();
      indexes.add(getIndex(timestamp));
    }
    for (String index : indexes) {
      sendCreateIndexRequest(index);
    }
  }

  private void checkIndexTemplateExistence() {
    String indexTemplate = analyticsIndexingConnector.getIndexTemplate();
    if (sendIsIndexTemplateExistsRequest()) {
      LOG.debug("Index Template {} already exists. Index creation requests will not be sent.",
                indexTemplate);
    } else {
      long startTime = System.currentTimeMillis();
      esIndexTemplateQuery = esIndexTemplateQuery.replace(AnalyticsIndexingServiceConnector.DEFAULT_ES_INDEX_TEMPLATE,
                                                          analyticsIndexingConnector.getIndexAlias())
                                                 .replace("replica.number",
                                                          String.valueOf(analyticsIndexingConnector.getReplicas()))
                                                 .replace("shard.number", String.valueOf(analyticsIndexingConnector.getShards()));
      ElasticResponse responseCreate = sendHttpPostRequest("_index_template/" + indexTemplate, esIndexTemplateQuery);
      auditTrail.audit("create_index_template",
                       null,
                       indexTemplate,
                       responseCreate.getStatusCode(),
                       responseCreate.getMessage(),
                       (System.currentTimeMillis() - startTime));

      if (sendIsIndexTemplateExistsRequest()) {
        LOG.info("Index Template {} created.", indexTemplate);
        analyticsIndexingConnector.storeCreatedIndexTemplate();
      } else {
        throw new IllegalStateException("Index Template " + indexTemplate
            + " isn't created successfully");
      }
    }
  }

  private final String getIndex(long timestamp) {
    if (indexPerDays > 0) {
      String indexSuffix = getIndexSuffix(timestamp);
      return analyticsIndexingConnector.getIndexPrefix() + "_" + indexSuffix;
    } else {
      return null;
    }
  }

  private String getFileContent(ConfigurationManager configurationManager, String filePath) throws Exception {
    InputStream mappingFileIS = configurationManager.getInputStream(filePath);
    return IOUtil.getStreamContentAsString(mappingFileIS);
  }

}
