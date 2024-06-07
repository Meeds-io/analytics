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

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.elasticsearch.model.ElasticResponse;
import io.meeds.analytics.model.StatisticDataQueueEntry;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class AnalyticsESClient {

  private static final Log              LOG                               =
                                            ExoLogger.getExoLogger(AnalyticsESClient.class);

  private static final String           DEFAULT_ES_CLIENT_SERVER_URL      = "http://127.0.0.1:9200";

  public static final int               DEFAULT_MAX_HTTP_POOL_CONNECTIONS = 100;

  private static final String           ES_CLIENT_SERVER_URL              = "exo.es.index.server.url";

  private static final String           ES_CLIENT_USERNAME                = "exo.es.index.server.username";

  private static final String           ES_CLIENT_PWD                     = "exo.es.index.server.password";                            // NOSONAR

  private static final long             DAY_IN_MS                         = 86400000L;

  private static final String           DAY_DATE_FORMAT                   = "yyyy-MM-dd";

  public static final DateTimeFormatter DAY_DATE_FORMATTER                = DateTimeFormatter.ofPattern(DAY_DATE_FORMAT)
                                                                                             .withResolverStyle(ResolverStyle.LENIENT);

  @Autowired
  private ConfigurationManager          configurationManager;

  @Autowired
  private AnalyticsIndexingConnector    analyticsIndexingConnector;

  protected String                      urlClient;

  protected HttpClient                  client;

  @Value("${analytics.es.index.template.path:jar:/analytics-es-template.json}")
  private String                        mappingFilePath;

  @Value("${analytics.es.index.server.username:}")
  private String                        username;

  @Value("${analytics.es.index.server.password:}")
  private String                        password;

  @Value("${analytics.es.index.per.days:7}")
  private int                           indexPerDays;

  private String                        esIndexTemplateQuery;

  @PostConstruct
  public void init() {
    if (StringUtils.isBlank(this.urlClient)) {
      this.urlClient = System.getProperty(ES_CLIENT_SERVER_URL);
      this.username = System.getProperty(ES_CLIENT_USERNAME);
      this.password = System.getProperty(ES_CLIENT_PWD);
    }
    if (StringUtils.isBlank(this.urlClient)) {
      this.urlClient = DEFAULT_ES_CLIENT_SERVER_URL;
    }
    this.client = getHttpClient();

    checkIndexTemplateExistence();
    LOG.info("Analytics client initialized and is ready to proceed analytics data");
  }

  public boolean sendCreateIndexRequest() {
    String index = getIndex();
    if (sendIsIndexExistsRequest(index)) {
      LOG.debug("Index {} already exists. Index creation requests will not be sent.", index);
      return false;
    } else {
      sendTurnOffWriteOnAllAnalyticsIndexes();

      ElasticResponse createIndexResponse = sendCreateIndex(index);
      if (sendIsIndexExistsRequest(index)) {
        LOG.info("New analytics index {} created.", index);
        return true;
      } else {
        throw new IllegalStateException("Error creating index " + index + " on elasticsearch, response code = " +
            createIndexResponse.getStatusCode() + " , response content : " + createIndexResponse.getMessage());
      }
    }
  }

  public void sendTurnOffWriteOnAllAnalyticsIndexes() {
    if (sendIsIndexExistsRequest(analyticsIndexingConnector.getIndexAlias())) {
      String esQuery = analyticsIndexingConnector.getTurnOffWriteOnAllAnalyticsIndexes();
      try {
        sendPostRequest("_aliases", esQuery);
        LOG.info("All analytics indexes switched to RO mode to prepare creation of a new index");
      } catch (Exception e) {
        LOG.warn("Analytics old indexes seems to not be turned off on write access");
      }
    }
  }

  @Cacheable("analytics.indexExists")
  public boolean sendIsIndexExistsRequest(String esIndex) {
    ElasticResponse responseExists = sendGetRequest(esIndex, false);
    return responseExists.getStatusCode() == HttpStatus.SC_OK;
  }

  @CacheEvict("analytics.indexExists")
  private ElasticResponse sendCreateIndex(String index) {
    String esIndexSettings = analyticsIndexingConnector.getCreateIndexRequestContent();
    return sendPutRequest(index, esIndexSettings);
  }

  public boolean sendIsIndexTemplateExistsRequest() {
    ElasticResponse responseExists = sendGetRequest("_index_template/" + analyticsIndexingConnector.getIndexTemplate(), false);
    return responseExists.getStatusCode() == HttpStatus.SC_OK;
  }

  public void sendCreateBulkDocumentsRequest(List<StatisticDataQueueEntry> dataQueueEntries) {
    if (dataQueueEntries == null || dataQueueEntries.isEmpty()) {
      return;
    }

    LOG.info("Indexing in bulk {} documents", dataQueueEntries.size());
    sendCreateIndexRequest();

    StringBuilder request = new StringBuilder();
    for (StatisticDataQueueEntry statisticDataQueueEntry : dataQueueEntries) {
      String documentId = String.valueOf(statisticDataQueueEntry.getId());
      String singleDocumentQuery = analyticsIndexingConnector.getCreateDocumentRequestContent(documentId);
      request.append(singleDocumentQuery);
    }

    LOG.debug("Create documents request to ES: {}", request);
    sendPutRequest("_bulk", request.toString());

    refreshIndex();
  }

  public String sendRequest(String esQuery) {
    ElasticResponse elasticResponse = sendPostRequest(analyticsIndexingConnector.getIndexAlias() + "/_search", esQuery);
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
    ElasticResponse response = sendGetRequest(analyticsIndexingConnector.getIndexAlias() + "/_mapping", false);
    if (isError(response)) {
      LOG.warn("Error getting mapping of analytics : - \t\tcode : {} - \t\tmessage: {}",
               response.getStatusCode(),
               response.getMessage());
      return null;
    } else {
      return response.getMessage();
    }
  }

  public void refreshIndex() {
    refreshIndex(analyticsIndexingConnector.getIndexAlias());
  }

  public void refreshIndex(String index) {
    sendPostRequest(index + "/_refresh", null);
  }

  public int getIndexPerDays() {
    return indexPerDays;
  }

  protected String getEsUsernameProperty() {
    return username;
  }

  protected String getEsPasswordProperty() {
    return password;
  }

  public ElasticResponse sendGetRequest(String uri) {
    return sendGetRequest(uri, true);
  }

  public ElasticResponse sendGetRequest(String uri, boolean handleResponse) {
    ElasticResponse response = sendHttpGetRequest(urlClient + "/" + uri);
    if (handleResponse) {
      return handleESResponse(response);
    } else {
      return response;
    }
  }

  protected ElasticResponse sendHeadRequest(String uri) {
    ElasticResponse response = sendHttpHeadRequest(urlClient + "/" + uri);
    return handleESResponse(response);
  }

  public ElasticResponse sendPutRequest(String uri, String content) {
    ElasticResponse response = sendHttpPutRequest(urlClient + "/" + uri, content);
    return handleESResponse(response);
  }

  public ElasticResponse sendDeleteRequest(String uri) {
    ElasticResponse response = sendHttpDeleteRequest(urlClient + "/" + uri);
    return handleESResponse(response);
  }

  public ElasticResponse sendPostRequest(String uri, String content) {
    ElasticResponse response = sendHttpPostRequest(urlClient + "/" + uri, content);
    return handleESResponse(response);
  }

  @SneakyThrows
  protected ElasticResponse sendHttpPostRequest(String url, String content) {
    HttpPost httpTypeRequest = new HttpPost(url);
    if (StringUtils.isNotBlank(content)) {
      httpTypeRequest.setEntity(new StringEntity(content, ContentType.APPLICATION_JSON));
    }
    return client.execute(httpTypeRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  protected ElasticResponse sendHttpPutRequest(String url, String content) {
    HttpPut httpTypeRequest = new HttpPut(url);
    if (StringUtils.isNotBlank(content)) {
      httpTypeRequest.setEntity(new StringEntity(content, ContentType.APPLICATION_JSON));
    }
    return client.execute(httpTypeRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  protected ElasticResponse sendHttpDeleteRequest(String url) {
    HttpDelete httpDeleteRequest = new HttpDelete(url);
    return client.execute(httpDeleteRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  protected ElasticResponse sendHttpGetRequest(String url) {
    HttpGet httpGetRequest = new HttpGet(url);
    return client.execute(httpGetRequest, this::handleHttpResponse);
  }

  @SneakyThrows
  protected ElasticResponse sendHttpHeadRequest(String url) {
    HttpHead httpHeadRequest = new HttpHead(url);
    return client.execute(httpHeadRequest, this::handleHttpResponse);
  }

  /**
   * Handle Http response receive from ES Log an INFO if the return status code
   * is 2xx Log an ERROR if the return code is different from 2xx
   *
   * @param httpResponse The Http Response to handle
   */
  @SneakyThrows
  private ElasticResponse handleHttpResponse(ClassicHttpResponse httpResponse) throws IOException {
    final HttpEntity entity = httpResponse.getEntity();
    int statusCode = httpResponse.getCode();
    return new ElasticResponse(EntityUtils.toString(entity), statusCode);
  }

  protected int getMaxConnections() {
    return DEFAULT_MAX_HTTP_POOL_CONNECTIONS;
  }

  protected HttpClientConnectionManager getClientConnectionManager() {
    return new PoolingHttpClientConnectionManager();
  }

  private boolean isError(ElasticResponse response) {
    return isError(response.getStatusCode());
  }

  private boolean isError(int status) {
    return status / 100 != 2;
  }

  private ElasticResponse handleESResponse(ElasticResponse response) {
    if (isError(response) || StringUtils.contains(response.getMessage(), "\"errors\":true")) {
      throw new IllegalStateException(response.getMessage());
    } else if (StringUtils.contains(response.getMessage(), "\"type\":\"version_conflict_engine_exception\"")) {
      LOG.warn("ID conflict in some content: {}", response.getMessage());
    }
    return response;
  }

  private void checkIndexTemplateExistence() {
    if (!sendIsIndexTemplateExistsRequest()) {
      String indexTemplate = analyticsIndexingConnector.getIndexTemplate();
      sendPostRequest("_index_template/" + indexTemplate, getEsIndexTemplateQuery());
      if (sendIsIndexTemplateExistsRequest()) {
        analyticsIndexingConnector.storeCreatedIndexTemplate();
        LOG.info("Index Template {} created.", indexTemplate);
      } else {
        throw new IllegalStateException("Error while creating Index Template " + indexTemplate);
      }
    }
  }

  private final String getIndex() {
    return getIndex(System.currentTimeMillis() / getIndexPerDaysMs());
  }

  @Cacheable("analytics.indexName")
  private final String getIndex(long indexPeriodIndex) {
    long periodEpochMs = indexPeriodIndex * getIndexPerDaysMs();
    String indexSuffix = DAY_DATE_FORMATTER.format(Instant.ofEpochMilli(periodEpochMs)
                                                          .atZone(ZoneOffset.UTC));
    return analyticsIndexingConnector.getIndexPrefix() + "_" + indexSuffix;
  }

  private long getIndexPerDaysMs() {
    return DAY_IN_MS * Math.max(indexPerDays, 1);
  }

  @SneakyThrows
  private String getEsIndexTemplateQuery() {
    if (StringUtils.isBlank(esIndexTemplateQuery)) {
      InputStream mappingFileIS = configurationManager.getInputStream(mappingFilePath);
      esIndexTemplateQuery = IOUtil.getStreamContentAsString(mappingFileIS);
      esIndexTemplateQuery = esIndexTemplateQuery.replace(AnalyticsIndexingConnector.DEFAULT_ES_INDEX_TEMPLATE,
                                                          analyticsIndexingConnector.getIndexAlias())
                                                 .replace("replica.number",
                                                          String.valueOf(analyticsIndexingConnector.getReplicas()))
                                                 .replace("shard.number", String.valueOf(analyticsIndexingConnector.getShards()));

    }
    return esIndexTemplateQuery;
  }

  protected HttpClient getHttpClient() {
    // Check if Basic Authentication need to be used
    HttpClientConnectionManager clientConnectionManager = getClientConnectionManager();
    HttpClientBuilder httpClientBuilder = HttpClientBuilder
                                                           .create()
                                                           .disableAutomaticRetries()
                                                           .setConnectionManager(clientConnectionManager)
                                                           .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy());
    if (StringUtils.isNotBlank(getEsUsernameProperty())) {
      BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(
                                   new AuthScope(null, -1),
                                   new UsernamePasswordCredentials(getEsUsernameProperty(),
                                                                   getEsPasswordProperty().toCharArray()));

      HttpClient httpClient = httpClientBuilder
                                               .setDefaultCredentialsProvider(credsProvider)
                                               .build();
      LOG.debug("Basic authentication for ES activated with username = {}",
                getEsUsernameProperty());
      return httpClient;
    } else {
      LOG.debug("Basic authentication for ES not activated");
      return httpClientBuilder.build();
    }
  }

}
