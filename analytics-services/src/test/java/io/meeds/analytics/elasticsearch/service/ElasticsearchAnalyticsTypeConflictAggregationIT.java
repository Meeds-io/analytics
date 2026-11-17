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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import io.meeds.analytics.elasticsearch.AbstractElasticsearchIT;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchConfiguration;
import io.meeds.analytics.model.StatisticFieldValue;
import io.meeds.analytics.model.chart.ChartAggregationResult;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * A server whose statistics started before the index template typed
 * {@code spaceId} and {@code userId} as {@code keyword} holds them as
 * {@code long} in its older weekly indices and as {@code keyword} in the newer
 * ones. Executed against a real engine: an aggregation on such a field reads
 * the indices of one type only, with no error and no failed shard, so a
 * grouping by space or by user loses the other weeks and a distinct count
 * counts the same space or user once per type.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchAnalyticsTypeConflictAggregationIT extends AbstractElasticsearchIT {

  private static final String           PREFIX         = "it_conflict_analytics";

  private static final String           ALIAS          = PREFIX + "_alias";

  private static final String           OLD_INDEX      = PREFIX + "_2020-01-02";

  private static final long             INDEX_PER_DAYS = 7;

  private final HttpClient              client         = HttpClient.newBuilder()
                                                                   .connectTimeout(Duration.ofSeconds(5))
                                                                   .build();

  @Mock
  private ElasticsearchConfiguration    configuration;

  @Mock
  private ListenerService               listenerService;

  @Mock
  private SettingService                settingService;

  private ElasticsearchAnalyticsStorage storage;

  private ElasticsearchAnalyticsService service;

  @BeforeEach
  void setUp() throws Exception {
    String url = elasticsearchUrl();
    when(configuration.getUrlClient()).thenReturn(url);
    when(configuration.getIndexPrefix()).thenReturn(PREFIX);
    when(configuration.getIndexAlias()).thenReturn(ALIAS);
    when(configuration.getIndexPerDays()).thenReturn(INDEX_PER_DAYS);
    when(configuration.getMaxIndexCount()).thenReturn(500L);

    storage = new ElasticsearchAnalyticsStorage();
    ReflectionTestUtils.setField(storage, "elasticsearchConfiguration", configuration);
    ReflectionTestUtils.setField(storage, "listenerService", listenerService);
    ReflectionTestUtils.setField(storage, "httpClient", HttpClientBuilder.create().build());

    service = new ElasticsearchAnalyticsService();
    ReflectionTestUtils.setField(service, "elasticsearchStorage", storage);
    ReflectionTestUtils.setField(service, "settingService", settingService);
    ReflectionTestUtils.setField(service, "aggregationReturnedDocumentsSize", 200);

    cleanCluster();
    // The older weeks hold the ids as numbers, the newer ones as keywords
    createIndex(OLD_INDEX, "long", "float", "\"type\":\"date\",\"format\":\"epoch_millis\"", false);
    createIndex(currentIndex(), "keyword", "long", "\"type\":\"long\"", true);
    index(OLD_INDEX, "1", event("createActivity", 1000, 1, 7));
    index(OLD_INDEX, "2", event("createActivity", 2000, 2, 7));
    index(currentIndex(), "3", event("createActivity", 4000, "1", "7"));
    index(currentIndex(), "4", event("createComment", 5000, "3", "8"));
    send("POST", "/" + ALIAS + "/_refresh", null);
    service.mergeIndicesMappings(storage.retrieveAllAnalyticsIndexesMapping());
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanCluster();
  }

  @Test
  void theLastActivityOfEachSpaceIsReadFromTheIndicesOfBothTypes() {
    ChartDataList lastActivities = service.getChart(null,
                                                    null,
                                                    null,
                                                    "spaceId",
                                                    AnalyticsAggregationType.TERMS,
                                                    null,
                                                    "timestamp",
                                                    AnalyticsAggregationType.MAX,
                                                    "desc",
                                                    10,
                                                    false);

    List<ChartAggregationResult> results = results(lastActivities);
    assertEquals(List.of("3", "1", "2"),
                 results.stream().map(ChartAggregationResult::getLabel).toList(),
                 "the space active in the newer index only comes first, the one of the older index only is still listed");
    assertEquals(List.of(5000d, 4000d, 2000d),
                 results.stream().map(result -> Double.parseDouble(result.getResult())).toList(),
                 "a space of both indices takes its newest activity");
  }

  @Test
  void theEventsOfAUserAreCountedOverTheIndicesOfBothTypes() {
    ChartDataList eventsByUser = service.getChart(null,
                                                  null,
                                                  null,
                                                  "userId",
                                                  AnalyticsAggregationType.TERMS,
                                                  null,
                                                  "operation",
                                                  AnalyticsAggregationType.COUNT,
                                                  "desc",
                                                  10,
                                                  false);

    Map<String, Double> countByUser = results(eventsByUser).stream()
                                                           .collect(Collectors.toMap(ChartAggregationResult::getLabel,
                                                                                     result -> Double.parseDouble(result.getResult())));
    assertEquals(Map.of("7", 3d, "8", 1d), countByUser, "the user of both indices is one group holding its three events");
  }

  @Test
  void theValuesOfAFieldAreListedFromTheIndicesOfBothTypes() {
    List<StatisticFieldValue> users = service.retrieveFieldValues("userId", 10);

    assertEquals(Map.of("7", 3L, "8", 1L),
                 users.stream().collect(Collectors.toMap(StatisticFieldValue::getValue, StatisticFieldValue::getCount)),
                 "each user is listed once, with the events of both indices");
  }

  @Test
  void aUserOfBothTypesIsCountedOnceAmongTheDistinctUsers() {
    ChartDataList distinctUsers = service.getChart(null,
                                                   null,
                                                   null,
                                                   "module",
                                                   AnalyticsAggregationType.TERMS,
                                                   null,
                                                   "userId",
                                                   AnalyticsAggregationType.CARDINALITY,
                                                   "desc",
                                                   10,
                                                   false);

    List<ChartAggregationResult> results = results(distinctUsers);
    assertEquals(List.of("social"), results.stream().map(ChartAggregationResult::getLabel).toList());
    assertEquals(2d,
                 Double.parseDouble(results.getFirst().getResult()),
                 "the user of both indices is one user, beside the user of the newer index");
  }

  @Test
  void aUserOfBothTypesIsOneGroupOfAGroupByCount() {
    AnalyticsFilter filter = new AnalyticsFilter();
    filter.setFilters(new ArrayList<>());
    filter.addXAxisAggregation(new AnalyticsAggregation(AnalyticsAggregationType.TERMS, "module", "desc", null, 10));
    AnalyticsAggregation groupBy = new AnalyticsAggregation();
    groupBy.setType(AnalyticsAggregationType.GROUP_BY);
    groupBy.setField("userId");
    groupBy.setMinDocCount(1);
    filter.setYAxisAggregation(groupBy);

    List<ChartAggregationResult> results = results(service.computeChartData(filter));

    assertEquals(1, results.size(), "one module holds the events");
    assertEquals(2d,
                 Double.parseDouble(results.getFirst().getResult()),
                 "the user of both indices is one group, beside the user of the newer index");
  }

  @Test
  void aWholeNumberIsOneGroupWhetherTheIndexHoldsItAsADecimalOrAsAnInteger() {
    ChartDataList eventsByRank = service.getChart(null,
                                                  null,
                                                  null,
                                                  "rank",
                                                  AnalyticsAggregationType.TERMS,
                                                  null,
                                                  "operation",
                                                  AnalyticsAggregationType.COUNT,
                                                  "desc",
                                                  10,
                                                  false);

    Map<String, Double> countByRank = results(eventsByRank).stream()
                                                           .collect(Collectors.toMap(ChartAggregationResult::getLabel,
                                                                                     result -> Double.parseDouble(result.getResult())));
    assertEquals(Map.of("15", 4d), countByRank, "15.0 of the float index and 15 of the long index are the same rank");
  }

  @Test
  void aMomentIsOneGroupWhetherTheIndexHoldsItAsADateOrAsANumber() {
    ChartDataList eventsByMoment = service.getChart(null,
                                                    null,
                                                    null,
                                                    "moment",
                                                    AnalyticsAggregationType.TERMS,
                                                    null,
                                                    "operation",
                                                    AnalyticsAggregationType.COUNT,
                                                    "desc",
                                                    10,
                                                    false);

    Map<String, Double> countByMoment = results(eventsByMoment).stream()
                                                               .collect(Collectors.toMap(ChartAggregationResult::getLabel,
                                                                                         result -> Double.parseDouble(result.getResult())));
    assertEquals(Map.of("1000", 4d), countByMoment, "the date of the older index and the number of the newer one are the same moment");
  }

  @Test
  void aMaximumOfAFieldInTypeConflictKeepsReadingTheFieldItself() {
    ChartDataList highestRank = service.getChart(null,
                                                 null,
                                                 null,
                                                 "module",
                                                 AnalyticsAggregationType.TERMS,
                                                 null,
                                                 "rank",
                                                 AnalyticsAggregationType.MAX,
                                                 "desc",
                                                 10,
                                                 false);

    List<ChartAggregationResult> results = results(highestRank);
    assertEquals(List.of("social"), results.stream().map(ChartAggregationResult::getLabel).toList());
    assertEquals(15d, Double.parseDouble(results.getFirst().getResult()), "a metric reads numbers, never the text of the grouping");
  }

  /**
   * The behaviour of the engine the fix answers, pinned here because nothing
   * else states it: the plain request is accepted, no shard fails, and the
   * answer misses the spaces of one type.
   */
  @Test
  void theEngineAnswersAPlainGroupingFromTheIndicesOfOneTypeWithNoFailedShard() throws Exception {
    HttpResponse<String> response = send("POST", "/" + ALIAS + "/_search", """
        {"size":0,"aggs":{"spaces":{"terms":{"field":"spaceId"}}}}
        """);

    assertEquals(200, response.statusCode(), response::body);
    JSONObject answer = new JSONObject(response.body());
    assertEquals(0, answer.getJSONObject("_shards").getInt("failed"), response::body);
    int spaces = answer.getJSONObject("aggregations").getJSONObject("spaces").getJSONArray("buckets").length();
    assertTrue(spaces < 3, () -> "the three spaces are not all listed by the plain request: " + response.body());
  }

  private static List<ChartAggregationResult> results(ChartDataList chartData) {
    assertEquals(1, chartData.getCharts().size(), "one chart is expected");
    return chartData.getCharts().getFirst().getAggregationResults();
  }

  private static JSONObject event(String operation, long timestamp, Object spaceId, Object userId) {
    JSONObject event = new JSONObject();
    event.put("timestamp", timestamp);
    event.put("userId", userId);
    event.put("spaceId", spaceId);
    event.put("module", "social");
    event.put("subModule", "activity");
    event.put("operation", operation);
    event.put("status", 0);
    event.put("rank", 15);
    event.put("moment", 1000);
    return event;
  }

  private void index(String index, String id, JSONObject event) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + index + "/_doc/" + id, event.toString());
    assertTrue(response.statusCode() == 200 || response.statusCode() == 201, response::body);
  }

  private void createIndex(String index, String idType, String rankType, String momentMapping, boolean writeIndex) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + index, """
        {"aliases":{"%s":{"is_write_index":%s}},
         "settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{
           "timestamp":{"type":"date","format":"epoch_millis"},
           "userId":{"type":"%s"}, "spaceId":{"type":"%s"}, "rank":{"type":"%s"}, "moment":{%s}, "status":{"type":"short"},
           "operation":{"type":"keyword"}, "module":{"type":"keyword"}, "subModule":{"type":"keyword"}}}}
        """.formatted(ALIAS, writeIndex, idType, idType, rankType, momentMapping));
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
