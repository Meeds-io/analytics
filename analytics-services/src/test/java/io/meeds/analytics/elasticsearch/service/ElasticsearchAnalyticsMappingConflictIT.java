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
package io.meeds.analytics.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.elasticsearch.AbstractElasticsearchIT;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchConfiguration;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.model.StatisticFieldMapping;

/**
 * The EXO-90504 incident, executed against a real engine: after a weekly
 * rollover the write index mapped {@code profileProperties.country} as
 * {@code long} (a numeric-looking value came first, under a template that
 * still allowed numeric detection) while the older indices held it as
 * {@code keyword}. Every later {@code "France"} was refused and lost, because
 * the mapping view the documents were built against did not see the newer
 * type.
 * <p>
 * Everything below goes through the storage's own HTTP client and requests;
 * the JDK client is used only to prepare the cluster and to read it back.
 * The service is subclassed to keep its mapping view in the test instead of
 * going through {@code retrieveMapping}'s container-transactional wrapper,
 * which needs a Kernel this harness does not boot: the merge, the storage
 * and the processor's retry are the code under test, the wrapper is not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchAnalyticsMappingConflictIT extends AbstractElasticsearchIT {

  private static final String                        PREFIX        = "it_analytics";

  private static final String                        ALIAS         = PREFIX + "_alias";

  private static final String                        TEMPLATE      = PREFIX + "_template";

  private static final String                        OLD_INDEX     = PREFIX + "_2020-01-02";

  private static final String                        COUNTRY       = "profileProperties.country";

  private static final String                        COUNTRY_ALT   = COUNTRY + "_alt";

  private static final long                          INDEX_PER_DAYS = 7;

  private final HttpClient                           client        = HttpClient.newBuilder()
                                                                               .connectTimeout(Duration.ofSeconds(5))
                                                                               .build();

  @Mock
  private ElasticsearchConfiguration                 configuration;

  @Mock
  private ListenerService                            listenerService;

  @Mock
  private SettingService                             settingService;

  private ElasticsearchAnalyticsStorage              storage;

  private ViewHoldingAnalyticsService                service;

  private ElasticsearchStatisticDataProcessorService processor;

  @BeforeEach
  void setUp() throws Exception {
    String url = elasticsearchUrl();
    when(configuration.getUrlClient()).thenReturn(url);
    when(configuration.getIndexPrefix()).thenReturn(PREFIX);
    when(configuration.getIndexAlias()).thenReturn(ALIAS);
    when(configuration.getIndexTemplateName()).thenReturn(TEMPLATE);
    when(configuration.getIndexPerDays()).thenReturn(INDEX_PER_DAYS);
    when(configuration.getMaxIndexCount()).thenReturn(500L);
    when(configuration.getIndexTemplateMapping()).thenReturn(productTemplate());

    storage = new ElasticsearchAnalyticsStorage();
    ReflectionTestUtils.setField(storage, "elasticsearchConfiguration", configuration);
    ReflectionTestUtils.setField(storage, "listenerService", listenerService);
    ReflectionTestUtils.setField(storage, "httpClient", HttpClientBuilder.create().build());

    service = new ViewHoldingAnalyticsService();
    ReflectionTestUtils.setField(service, "elasticsearchStorage", storage);
    ReflectionTestUtils.setField(service, "settingService", settingService);
    ReflectionTestUtils.setField(service, "aggregationReturnedDocumentsSize", 200);

    processor = new ElasticsearchStatisticDataProcessorService();
    ReflectionTestUtils.setField(processor, "elasticsearchStorage", storage);
    ReflectionTestUtils.setField(processor, "elasticsearchAnalyticsService", service);
    // The production default, so the stale-view scenario proves the first
    // refusal still re-reads the mapping with the throttle in force.
    ReflectionTestUtils.setField(processor, "mappingRefreshMinIntervalSeconds", 30L);

    cleanCluster();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanCluster();
  }

  @Test
  void theNewestIndexTypeWinsAndAConflictingValueIsRoutedToTheAlternativeField() throws Exception {
    createIndex(OLD_INDEX, "keyword", false);
    createIndex(currentIndex(), "long", true);

    Map<String, StatisticFieldMapping> view = byName(service.retrieveMapping(true));
    assertEquals("long", view.get(COUNTRY).getType(), "the write index's type must win over the older keyword");
    assertTrue(view.get(COUNTRY).isTypeConflict());

    storage.sendCreateBulkDocumentsRequest(List.of(entry("France")), new HashSet<>(view.values()));

    assertEquals(1, countHits(COUNTRY_ALT, "France"), "the value must land in the alternative field, not be refused");
    assertEquals("keyword", fieldType(currentIndex(), COUNTRY_ALT), "the alternative field is mapped explicitly");
  }

  @Test
  void aStaleMappingViewIsRefreshedAndTheBulkRetriedOnce() throws Exception {
    createIndex(OLD_INDEX, "keyword", true);
    Map<String, StatisticFieldMapping> staleView = byName(service.retrieveMapping(true));
    assertEquals("keyword", staleView.get(COUNTRY).getType(), "precondition: the view predates the rollover");

    // The rollover, in the storage's own order: the previous index leaves
    // write mode, then the new write index is created, and its first document
    // mapped the field as long. The periodic refresh has not run yet.
    switchWriteIndex(OLD_INDEX, false);
    createIndex(currentIndex(), "long", true);

    // A mixed batch: the first bulk creates the event without a country and
    // refuses the other; only the refused one is retried.
    processor.process(List.of(entry("France"), entry(null)));

    assertEquals(1, countHits(COUNTRY_ALT, "France"), "one refresh and one retry must be enough to index the event");
    assertEquals(2, countDocuments(), "both events are indexed exactly once");
    assertEquals("long", byName(service.retrieveMapping(false)).get(COUNTRY).getType(), "the view was refreshed");
  }

  @Test
  void theIndexTemplateIsUpdatedAtStartupAndANewIndexNoLongerGuessesNumbers() throws Exception {
    // The template a cluster provisioned before MEED-9542 still holds.
    send("PUT", "/_index_template/" + TEMPLATE, productTemplate().replace("\"numeric_detection\": false",
                                                                          "\"numeric_detection\": true"));
    assertTrue(templateMapping().getBoolean("numeric_detection"), "precondition: the old template guesses numbers");

    storage.init();

    assertFalse(templateMapping().getBoolean("numeric_detection"), "startup must push the product's template");

    // A numeric-looking value coming first after a rollover no longer types
    // the field as a number for the whole week. The field is already known
    // to the platform (as the persisted mapping view would have it), so the
    // storage pushes no explicit mapping and the new index maps it
    // dynamically: that is the decision the template governs.
    Set<StatisticFieldMapping> knownView = Set.of(new StatisticFieldMapping(COUNTRY, "keyword", false));
    storage.sendCreateBulkDocumentsRequest(List.of(entry("12345")), knownView);
    assertEquals("text", fieldType(currentIndex(), COUNTRY), "no numeric guessing on the new weekly index");
    storage.sendCreateBulkDocumentsRequest(List.of(entry("France")), knownView);
    assertEquals(1, countHits(COUNTRY, "France"), "with the updated template the value is accepted");
  }

  /**
   * Keeps the mapping view in the test, where {@code retrieveMapping}'s
   * container-transactional wrapper would otherwise need a Kernel.
   */
  private class ViewHoldingAnalyticsService extends ElasticsearchAnalyticsService {

    private Set<StatisticFieldMapping> view = new HashSet<>();

    @Override
    public Set<StatisticFieldMapping> retrieveMapping(boolean forceRefresh) {
      if (forceRefresh || view.isEmpty()) {
        // Same guard as the production path: no index behind the alias yet
        // means no mapping to read, not an error.
        String indicesMapping = storage.retrieveAllAnalyticsIndexesMapping();
        if (StringUtils.isNotBlank(indicesMapping)) {
          view = mergeIndicesMappings(indicesMapping);
        }
      }
      return view;
    }
  }

  private static StatisticDataQueueEntry entry(String country) {
    StatisticData data = new StatisticData();
    data.setModule("portal");
    data.setSubModule("login");
    data.setOperation("login");
    data.setTimestamp(System.currentTimeMillis());
    data.setUserId(1);
    if (country != null) {
      data.addKeyword(COUNTRY, country);
    }
    return new StatisticDataQueueEntry(data);
  }

  private long countDocuments() throws Exception {
    send("POST", "/" + ALIAS + "/_refresh", null);
    HttpResponse<String> response = send("GET", "/" + ALIAS + "/_count", null);
    assertEquals(200, response.statusCode(), response::body);
    return new JSONObject(response.body()).getLong("count");
  }

  private static Map<String, StatisticFieldMapping> byName(Set<StatisticFieldMapping> mappings) {
    return mappings.stream().collect(Collectors.toMap(StatisticFieldMapping::getName, Function.identity()));
  }

  /** Same arithmetic as the storage, so the test owns the index it will write to */
  private static String currentIndex() {
    long periodMs = INDEX_PER_DAYS * 86400000L;
    long periodStart = System.currentTimeMillis() / periodMs * periodMs;
    return PREFIX + "_"
        + ElasticsearchAnalyticsStorage.DAY_DATE_FORMATTER.format(Instant.ofEpochMilli(periodStart).atZone(ZoneOffset.UTC));
  }

  private static String productTemplate() throws IOException {
    try (InputStream is = ElasticsearchAnalyticsMappingConflictIT.class.getClassLoader()
                                                                        .getResourceAsStream("analytics-es-template.json")) {
      assertNotNull(is, "the product template must be on the test classpath");
      return new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("\"analytics*\"", "\"" + PREFIX + "*\"")
                                                                   .replace("analytics_alias", ALIAS)
                                                                   .replace("shard.number", "1")
                                                                   .replace("replica.number", "0");
    }
  }

  private void createIndex(String index, String countryType, boolean writeIndex) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + index, """
        {"aliases":{"%s":{"is_write_index":%s}},
         "mappings":{"properties":{"profileProperties":{"properties":{"country":{"type":"%s"}}}}}}
        """.formatted(ALIAS, writeIndex, countryType));
    assertEquals(200, response.statusCode(), response::body);
  }

  private void switchWriteIndex(String index, boolean writeIndex) throws Exception {
    HttpResponse<String> response = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":%s}}]}
        """.formatted(index, ALIAS, writeIndex));
    assertEquals(200, response.statusCode(), response::body);
  }

  private long countHits(String field, String value) throws Exception {
    send("POST", "/" + ALIAS + "/_refresh", null);
    HttpResponse<String> response = send("POST", "/" + ALIAS + "/_search", """
        {"query":{"match":{"%s":"%s"}},"size":0,"track_total_hits":true}
        """.formatted(field, value));
    assertEquals(200, response.statusCode(), response::body);
    return new JSONObject(response.body()).getJSONObject("hits").getJSONObject("total").getLong("value");
  }

  private String fieldType(String index, String field) throws Exception {
    HttpResponse<String> response = send("GET", "/" + index + "/_mapping/field/" + field, null);
    assertEquals(200, response.statusCode(), response::body);
    JSONObject mapping = new JSONObject(response.body()).getJSONObject(index)
                                                        .getJSONObject("mappings")
                                                        .optJSONObject(field);
    if (mapping == null || mapping.isEmpty()) {
      return null;
    }
    String leaf = field.substring(field.lastIndexOf('.') + 1);
    return mapping.getJSONObject("mapping").getJSONObject(leaf).getString("type");
  }

  private JSONObject templateMapping() throws Exception {
    HttpResponse<String> response = send("GET", "/_index_template/" + TEMPLATE, null);
    assertEquals(200, response.statusCode(), response::body);
    return new JSONObject(response.body()).getJSONArray("index_templates")
                                          .getJSONObject(0)
                                          .getJSONObject("index_template")
                                          .getJSONObject("template")
                                          .getJSONObject("mappings");
  }

  private void cleanCluster() throws Exception {
    // A wildcard delete is refused by default since Elasticsearch 8
    // (action.destructive_requires_name), so the indices are resolved first
    // and deleted by name. Only the suite's own it_analytics_* names are
    // touched: an engine holding a platform's analytics_* indices is safe.
    HttpResponse<String> indices = send("GET", "/" + PREFIX + "_*?allow_no_indices=true", null);
    assertEquals(200, indices.statusCode(), indices::body);
    for (String index : new JSONObject(indices.body()).keySet()) {
      HttpResponse<String> deleted = send("DELETE", "/" + index, null);
      assertEquals(200, deleted.statusCode(), deleted::body);
    }
    HttpResponse<String> template = send("DELETE", "/_index_template/" + TEMPLATE, null);
    assertTrue(template.statusCode() == 200 || template.statusCode() == 404, template::body);
  }

  private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(elasticsearchUrl() + path))
                                             .timeout(Duration.ofSeconds(30))
                                             .header("Content-Type", "application/json");
    request.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    return client.send(request.build(), BodyHandlers.ofString());
  }

}
