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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.elasticsearch.AbstractElasticsearchIT;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchConfiguration;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.collaborative.CollaborativeActivityBrowserOperation;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDeclaration;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDefinition;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import io.meeds.analytics.service.CollaborativeActivityServiceImpl;
import io.meeds.analytics.utils.AnalyticsUtils;

/**
 * The collaborative-activity filter executed against a real engine, on two
 * weekly indexes under one alias: one where {@code activityType} is mapped
 * {@code keyword}, as the explicit mappings create it, and one where it is
 * mapped {@code text} with a {@code keyword} sub-field, as the dynamic
 * mapping typed it on the indexes created before the explicit mappings. A
 * unit test on the request body cannot show the engine accepting the
 * composed clause, nor the exclusion matching on the {@code text}-mapped
 * name, which is the case that would count every note's stream post twice.
 * <p>
 * Same harness as {@link ElasticsearchAnalyticsMappingConflictIT}: the suite's
 * own {@code it_analytics*} names, the storage's own client for the queries,
 * the JDK client to prepare the cluster.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollaborativeActivityFilterIT extends AbstractElasticsearchIT {

  private static final String             PREFIX         = "it_analytics";

  private static final String             ALIAS          = PREFIX + "_alias";

  private static final String             TEMPLATE       = PREFIX + "_template";

  private static final String             OLD_INDEX      = PREFIX + "_2020-01-02";

  private static final long               INDEX_PER_DAYS = 7;

  private static final String             SCRIPTED_ENTRY = "doc['timestamp'].value.dayOfWeek";

  private static final AnalyticsFieldFilter COLLABORATIVE  = new AnalyticsFieldFilter(CollaborativeActivityService.FIELD_NAME,
                                                                                      AnalyticsFieldFilterType.EQUAL,
                                                                                      CollaborativeActivityService.YES);

  private static final AnalyticsFieldFilter NOT_COLLABORATIVE =
                                                              new AnalyticsFieldFilter(CollaborativeActivityService.FIELD_NAME,
                                                                                       AnalyticsFieldFilterType.NOT_EQUAL,
                                                                                       CollaborativeActivityService.YES);

  private final HttpClient                client         = HttpClient.newBuilder()
                                                                     .connectTimeout(Duration.ofSeconds(5))
                                                                     .build();

  @Mock
  private ElasticsearchConfiguration      configuration;

  @Mock
  private ListenerService                 listenerService;

  @Mock
  private SettingService                  settingService;

  private ElasticsearchAnalyticsStorage   storage;

  private ElasticsearchAnalyticsService   service;

  private CollaborativeActivityServiceImpl collaborativeActivityService;

  /** The ids of the events the full definition counts, on both indexes */
  private final Set<String>               collaborativeIds = Set.of("1", "4", "7", "8", "10", "101", "104", "107", "108", "110");

  private final Set<String>               allIds           = Set.of("1", "2", "3", "4", "5", "6", "7", "8", "10",
                                                                    "101", "102", "103", "104", "105", "106", "107", "108",
                                                                    "109", "110");

  @BeforeEach
  void setUp() throws Exception {
    String url = elasticsearchUrl();
    when(configuration.getUrlClient()).thenReturn(url);
    when(configuration.getIndexPrefix()).thenReturn(PREFIX);
    when(configuration.getIndexAlias()).thenReturn(ALIAS);
    when(configuration.getIndexTemplateName()).thenReturn(TEMPLATE);
    when(configuration.getIndexPerDays()).thenReturn(INDEX_PER_DAYS);
    when(configuration.getMaxIndexCount()).thenReturn(500L);

    storage = new ElasticsearchAnalyticsStorage();
    ReflectionTestUtils.setField(storage, "elasticsearchConfiguration", configuration);
    ReflectionTestUtils.setField(storage, "listenerService", listenerService);
    ReflectionTestUtils.setField(storage, "httpClient", HttpClientBuilder.create().build());

    collaborativeActivityService = new CollaborativeActivityServiceImpl();
    applyDefinition(fullDefinition());

    service = new ElasticsearchAnalyticsService();
    ReflectionTestUtils.setField(service, "elasticsearchStorage", storage);
    ReflectionTestUtils.setField(service, "settingService", settingService);
    ReflectionTestUtils.setField(service, "collaborativeActivityService", collaborativeActivityService);
    ReflectionTestUtils.setField(service, "aggregationReturnedDocumentsSize", 200);

    cleanCluster();
    createIndex(OLD_INDEX, "\"activityType\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}}", false);
    createIndex(currentIndex(), "\"activityType\":{\"type\":\"keyword\"},\"activityType_alt\":{\"type\":\"keyword\"}", true);
    indexEvents(OLD_INDEX, 0);
    indexEvents(currentIndex(), 100);
    index(currentIndex(), "109", event("createActivity", Map.of("activityType_alt", "ks-wiki:spaces")));
    send("POST", "/" + ALIAS + "/_refresh", null);
    refreshViewFromTheEngine();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanCluster();
  }

  @Test
  void theClauseSelectsExactlyTheCollaborativeEventsOnBothIndexGenerations() {
    List<StatisticData> samples = service.retrieveData(List.of(COLLABORATIVE), 0, 100, null);

    assertEquals(collaborativeIds, ids(samples), "declared server operations, untyped posts, posts of a non-excluded type"
        + " and browser operations with or without an origin; never a note's stream post, a forged post or a forged"
        + " server operation, on the keyword index as on the text one");
    samples.forEach(sample -> assertEquals(CollaborativeActivityService.YES,
                                           sample.getKeyword(CollaborativeActivityService.FIELD_NAME),
                                           "each sample read back carries the virtual field: " + sample));
    StatisticData browserEvent = samples.stream().filter(sample -> "107".equals(sample.getKeyword("id"))).findFirst().orElseThrow();
    assertEquals("websocket", browserEvent.getKeyword(AnalyticsUtils.STATISTIC_ORIGIN), "the origin is read back with no error");
  }

  @Test
  void theNegatedFilterMatchesExactlyTheComplement() {
    Set<String> others = new java.util.HashSet<>(allIds);
    others.removeAll(collaborativeIds);

    assertEquals(others, ids(service.retrieveData(List.of(NOT_COLLABORATIVE), 0, 100, null)));
  }

  @Test
  void anAlternativeNameOfActivityTypeReportedByTheViewGetsTheSameExclusion() {
    assertTrue(ids(service.retrieveData(List.of(COLLABORATIVE), 0, 100, null)).stream().noneMatch("109"::equals),
               "the view read from the engine holds activityType_alt, so the post typed under it is excluded");

    // The same request with a view that does not know the variant: the
    // exclusion on the base name alone leaves that post counted. This is what
    // the variant clauses are for.
    Map<String, StatisticFieldMapping> withoutVariant = new HashMap<>(publishedView());
    withoutVariant.remove("activityType_alt");
    ElasticsearchAnalyticsServiceCollaborativeTest.viewOf(service).set(withoutVariant);
    assertTrue(ids(service.retrieveData(List.of(COLLABORATIVE), 0, 100, null)).contains("109"));
  }

  @Test
  void withNoBrowserOperationDeclaredTheRequestHasNoThirdBranchAndTheEngineAcceptsIt() {
    applyDefinition(new CollaborativeActivityDefinition(List.of(streamDeclaration(), serverDeclaration()),
                                                        List.of(),
                                                        List.of(),
                                                        List.of()));

    Set<String> ids = ids(service.retrieveData(List.of(COLLABORATIVE), 0, 100, null));

    assertEquals(Set.of("1", "4", "10", "101", "104", "110"), ids, "no sendMessage counts without a browser declaration");
  }

  @Test
  void withNoExcludedTypeDeclaredTheNoteStreamPostCounts() {
    CollaborativeActivityDeclaration server = serverDeclaration();
    server.setExcludedActivityTypes(List.of());
    applyDefinition(new CollaborativeActivityDefinition(List.of(streamDeclaration(), server, browserDeclaration()),
                                                        List.of(),
                                                        List.of(),
                                                        List.of()));

    Set<String> ids = ids(service.retrieveData(List.of(COLLABORATIVE), 0, 100, null));

    assertTrue(ids.containsAll(Set.of("3", "103", "109")), "nothing excludes the typed posts: " + ids);
    assertTrue(ids.containsAll(collaborativeIds));
  }

  @Test
  void aScriptedEntryFilterIsAcceptedWithNoHitAndItsNegationReturnsEveryEvent() {
    assertTrue(publishedView().containsKey(SCRIPTED_ENTRY), "precondition: the refreshed view holds the scripted entries");

    List<StatisticData> positive = service.retrieveData(List.of(new AnalyticsFieldFilter(SCRIPTED_ENTRY,
                                                                                         AnalyticsFieldFilterType.EQUAL,
                                                                                         "2")),
                                                        0,
                                                        100,
                                                        null);
    assertTrue(positive.isEmpty(), "match_none: the engine accepts the request and answers no hit");

    List<StatisticData> negative = service.retrieveData(List.of(new AnalyticsFieldFilter(SCRIPTED_ENTRY,
                                                                                         AnalyticsFieldFilterType.NOT_EQUAL,
                                                                                         "2")),
                                                        0,
                                                        100,
                                                        null);
    assertEquals(allIds, ids(negative), "no condition at all: every event");
  }

  @Test
  void collaborativeOnlyOnSamplesAndOnChartGoesThroughTheEngine() {
    List<StatisticData> samples = service.getSamples(null, null, null, "timestamp", "desc", 100, true);
    assertEquals(collaborativeIds, ids(samples));

    ChartDataList chart = service.getChart(null,
                                           null,
                                           null,
                                           "module",
                                           AnalyticsAggregationType.TERMS,
                                           "desc",
                                           "timestamp",
                                           AnalyticsAggregationType.MAX,
                                           "desc",
                                           10,
                                           true);
    assertEquals(0, chart.getFailedShards(), "every shard answered");
    assertEquals(collaborativeIds.size(), chart.getDataCount(), "the chart counts the same events");
  }

  private void refreshViewFromTheEngine() {
    service.mergeIndicesMappings(storage.retrieveAllAnalyticsIndexesMapping());
    assertTrue(publishedView().containsKey("activityType_alt"), "precondition: the view holds the variant of the new index");
  }

  private Map<String, StatisticFieldMapping> publishedView() {
    return ElasticsearchAnalyticsServiceCollaborativeTest.viewOf(service).get();
  }

  private void applyDefinition(CollaborativeActivityDefinition definition) {
    ElasticsearchAnalyticsServiceCollaborativeTest.definitionOf(collaborativeActivityService).set(definition);
  }

  private static CollaborativeActivityDefinition fullDefinition() {
    return new CollaborativeActivityDefinition(List.of(streamDeclaration(), serverDeclaration(), browserDeclaration()),
                                               List.of(),
                                               List.of(),
                                               List.of());
  }

  private static CollaborativeActivityDeclaration streamDeclaration() {
    CollaborativeActivityDeclaration declaration = new CollaborativeActivityDeclaration();
    declaration.setOperations(List.of("createActivity", "createComment"));
    return declaration;
  }

  private static CollaborativeActivityDeclaration serverDeclaration() {
    CollaborativeActivityDeclaration declaration = new CollaborativeActivityDeclaration();
    declaration.setOperations(List.of("taskCreated"));
    declaration.setExcludedActivityTypes(List.of("ks-wiki:spaces", "news"));
    return declaration;
  }

  private static CollaborativeActivityDeclaration browserDeclaration() {
    CollaborativeActivityDeclaration declaration = new CollaborativeActivityDeclaration();
    declaration.setBrowserOperations(List.of(new CollaborativeActivityBrowserOperation("sendMessage", "Chat")));
    return declaration;
  }

  private static Set<String> ids(List<StatisticData> samples) {
    return samples.stream().map(sample -> sample.getKeyword("id")).collect(Collectors.toSet());
  }

  /**
   * The ten events of one index generation: the same ten shapes, the id
   * offset telling the generations apart.
   */
  private void indexEvents(String index, int offset) throws Exception {
    index(index, String.valueOf(offset + 1), event("taskCreated", Map.of()));
    index(index, String.valueOf(offset + 2), event("likeActivity", Map.of()));
    index(index, String.valueOf(offset + 3), event("createActivity", Map.of("activityType", "ks-wiki:spaces")));
    index(index, String.valueOf(offset + 4), event("createActivity", Map.of()));
    index(index, String.valueOf(offset + 5), event("createActivity", Map.of("statisticOrigin", "websocket")));
    index(index, String.valueOf(offset + 6), event("taskCreated", Map.of("statisticOrigin", "websocket")));
    index(index, String.valueOf(offset + 7), event("sendMessage", Map.of("statisticOrigin", "websocket")));
    index(index, String.valueOf(offset + 8), event("sendMessage", Map.of()));
    index(index, String.valueOf(offset + 10), event("createActivity", Map.of("activityType", "files:spaces")));
  }

  private static JSONObject event(String operation, Map<String, String> parameters) {
    JSONObject event = new JSONObject();
    event.put("timestamp", System.currentTimeMillis());
    event.put("userId", 1);
    event.put("spaceId", 0);
    event.put("module", "m");
    event.put("subModule", "s");
    event.put("operation", operation);
    event.put("status", 0);
    event.put("errorCode", 0);
    event.put("duration", 0);
    event.put("isAnalytics", true);
    parameters.forEach(event::put);
    return event;
  }

  private void index(String index, String id, JSONObject event) throws Exception {
    event.put("id", Long.parseLong(id));
    HttpResponse<String> response = send("PUT", "/" + index + "/_doc/" + id, event.toString());
    assertTrue(response.statusCode() == 200 || response.statusCode() == 201, response::body);
  }

  private void createIndex(String index, String activityTypeMapping, boolean writeIndex) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + index, """
        {"aliases":{"%s":{"is_write_index":%s}},
         "settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{
           "id":{"type":"long"}, "timestamp":{"type":"date","format":"epoch_millis"},
           "userId":{"type":"long"}, "spaceId":{"type":"long"}, "status":{"type":"long"},
           "operation":{"type":"keyword"}, "module":{"type":"keyword"}, "statisticOrigin":{"type":"keyword"},
           %s}}}
        """.formatted(ALIAS, writeIndex, activityTypeMapping));
    assertEquals(200, response.statusCode(), response::body);
  }

  /** Same arithmetic as the storage, so the test owns the index it writes to */
  private static String currentIndex() {
    long periodMs = INDEX_PER_DAYS * 86400000L;
    long periodStart = System.currentTimeMillis() / periodMs * periodMs;
    return PREFIX + "_"
        + ElasticsearchAnalyticsStorage.DAY_DATE_FORMATTER.format(Instant.ofEpochMilli(periodStart).atZone(ZoneOffset.UTC));
  }

  private void cleanCluster() throws Exception {
    HttpResponse<String> indices = send("GET", "/" + PREFIX + "_*?allow_no_indices=true", null);
    assertEquals(200, indices.statusCode(), indices::body);
    for (String index : new JSONObject(indices.body()).keySet()) {
      HttpResponse<String> deleted = send("DELETE", "/" + index, null);
      assertEquals(200, deleted.statusCode(), deleted::body);
    }
  }

  private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(elasticsearchUrl() + path))
                                             .timeout(Duration.ofSeconds(30))
                                             .header("Content-Type", "application/json");
    request.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    return client.send(request.build(), BodyHandlers.ofString());
  }

}
