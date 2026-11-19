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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;

import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.chart.ChartData;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * Unit tests for the GROUP_BY (threshold aggregation) support added in
 * {@link ElasticsearchAnalyticsService}: the generated Elasticsearch query and
 * the parsing of its response.
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchAnalyticsServiceTest {

  private static final String           OLDEST_INDEX = "analytics_2026-07-30";

  private static final String           MIDDLE_INDEX = "analytics_2026-08-06";

  private static final String           NEWEST_INDEX = "analytics_2026-08-13";

  @Mock
  private SettingService                settingService;

  @Mock
  private ElasticsearchAnalyticsStorage elasticsearchStorage;

  private ElasticsearchAnalyticsService elasticsearchAnalyticsService;

  @BeforeEach
  void setUp() {
    elasticsearchAnalyticsService = new ElasticsearchAnalyticsService();
    ReflectionTestUtils.setField(elasticsearchAnalyticsService, "elasticsearchStorage", elasticsearchStorage);
    ReflectionTestUtils.setField(elasticsearchAnalyticsService, "settingService", settingService);
    ReflectionTestUtils.setField(elasticsearchAnalyticsService, "aggregationReturnedDocumentsSize", 200);
  }

  private AnalyticsFilter newGroupByFilter(long minDocCount) {
    AnalyticsFilter filter = new AnalyticsFilter();
    filter.setFilters(new ArrayList<>());
    filter.addXAxisAggregation(new AnalyticsAggregation(AnalyticsAggregationType.DATE, "timestamp", "asc", "month", 0));

    AnalyticsAggregation yAxisAggregation = new AnalyticsAggregation();
    yAxisAggregation.setType(AnalyticsAggregationType.GROUP_BY);
    yAxisAggregation.setField("userId");
    yAxisAggregation.setMinDocCount(minDocCount);
    filter.setYAxisAggregation(yAxisAggregation);
    return filter;
  }

  /**
   * The Elasticsearch request body is assembled by hand, as text blocks
   * concatenated across several append* methods: a misplaced brace or comma
   * yields a body that every contains() assertion below still accepts, and that
   * only a real Elasticsearch rejects. Parsing it here runs the query through
   * the grammar it must satisfy, which is what a unit suite can check without a
   * running cluster. It does not prove Elasticsearch accepts the aggregation
   * semantics: that still needs a run against a real index.
   */
  private String captureGeneratedQuery() {
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(elasticsearchStorage).search(queryCaptor.capture());
    String generatedQuery = queryCaptor.getValue();
    assertDoesNotThrow(() -> new JSONObject(generatedQuery),
                       () -> "The generated Elasticsearch query must be well-formed JSON, but was:\n" + generatedQuery);
    return generatedQuery;
  }

  private String cannedResponse(int groupByCount) {
    return """
        {
          "took": 5,
          "hits": {"total": {"value": 42}},
          "aggregations": {
            "aggregation_result": {
              "buckets": [
                {
                  "key": 1700000000000,
                  "doc_count": 10,
                  "aggregation_group_by": {"buckets": [{"key":"user1","doc_count":5},{"key":"user2","doc_count":3}]},
                  "aggregation_result_value": {"value": %d}
                }
              ]
            }
          }
        }
        """.formatted(groupByCount);
  }

  @Test
  void testGroupByAggregationGeneratesExpectedQuery() {
    when(elasticsearchStorage.search(anyString())).thenReturn(cannedResponse(2));

    AnalyticsFilter filter = newGroupByFilter(5);
    ChartDataList chartDataList = elasticsearchAnalyticsService.computeChartData(filter);

    String generatedQuery = captureGeneratedQuery();

    assertTrue(generatedQuery.contains("\"aggregation_group_by\""),
               "Query should contain the terms sub-aggregation for the distinct field");
    assertTrue(generatedQuery.contains("\"terms\""), "Query should use a terms aggregation for GROUP_BY");
    assertTrue(generatedQuery.contains("\"field\": \"userId\""), "Query should aggregate on the configured field");
    assertTrue(generatedQuery.contains("\"min_doc_count\": 5"), "Query should carry the configured threshold");
    assertTrue(generatedQuery.contains("\"bucket_script\""), "Query should count matching buckets via a bucket_script");
    assertTrue(generatedQuery.contains("\"aggregation_group_by._bucket_count\""),
               "bucket_script should reference the terms aggregation bucket count");

    List<ChartData> charts = new ArrayList<>(chartDataList.getCharts());
    assertEquals(1, charts.size());
    assertEquals(List.of("2"), charts.get(0).getValues());
  }

  @Test
  void testGroupByThresholdZeroIsFlooredToOne() {
    when(elasticsearchStorage.search(anyString())).thenReturn(cannedResponse(4));

    // A threshold of 0 (or unset/negative) must never be sent as-is to ES:
    // it would make ES return every distinct value with zero occurrences,
    // which is both meaningless and costly on a large dataset.
    AnalyticsFilter filter = newGroupByFilter(0);
    elasticsearchAnalyticsService.computeChartData(filter);

    String generatedQuery = captureGeneratedQuery();

    assertTrue(generatedQuery.contains("\"min_doc_count\": 1"),
               "A minDocCount <= 0 must be floored to 1, never sent as 0 or omitted");
    assertFalse(generatedQuery.contains("\"min_doc_count\": 0"),
                "min_doc_count must never be 0 (would scan every empty bucket)");
  }

  @Test
  void testGroupByIsNotUsedToSortAPrecedingTermsAggregation() {
    when(elasticsearchStorage.search(anyString())).thenReturn(cannedResponse(3));

    // X axis is a TERMS aggregation (e.g. grouping by module), Y axis is
    // GROUP_BY: ES rejects ordering a terms aggregation by a pipeline
    // aggregation such as GROUP_BY's bucket_script ("Invalid aggregation
    // order path ... is a pipeline aggregation and cannot be used to sort
    // the buckets"), so no such order clause must ever be generated.
    AnalyticsFilter filter = new AnalyticsFilter();
    filter.setFilters(new ArrayList<>());
    filter.addXAxisAggregation(new AnalyticsAggregation(AnalyticsAggregationType.TERMS, "module", "desc", null, 200));

    AnalyticsAggregation yAxisAggregation = new AnalyticsAggregation();
    yAxisAggregation.setType(AnalyticsAggregationType.GROUP_BY);
    yAxisAggregation.setField("userId");
    yAxisAggregation.setMinDocCount(1);
    filter.setYAxisAggregation(yAxisAggregation);

    elasticsearchAnalyticsService.computeChartData(filter);

    String generatedQuery = captureGeneratedQuery();

    assertFalse(generatedQuery.contains("\"order\": {\"aggregation_result_value"),
                "Terms aggregation must not be ordered by the GROUP_BY pipeline aggregation");
  }

  @Test
  void mergeIndicesMappingsPrefersTheExplicitTypeOverADynamicText() throws Exception {
    Map<String, StatisticFieldMapping> mappings = byName(elasticsearchAnalyticsService.mergeIndicesMappings(indicesMapping(
                                                                                                                           // listed
                                                                                                                           // out
                                                                                                                           // of
                                                                                                                           // date
                                                                                                                           // order
                                                                                                                           // on
                                                                                                                           // purpose:
                                                                                                                           // the
                                                                                                                           // merge
                                                                                                                           // sorts
                                                                                                                           // by
                                                                                                                           // index
                                                                                                                           // date
                                                                                                                           MIDDLE_INDEX,
                                                                                                                           properties(text("contentId"),
                                                                                                                                      longField("contentUpdatedDate_alt2"),
                                                                                                                                      keyword("module")),
                                                                                                                           NEWEST_INDEX,
                                                                                                                           properties(text("contentId"),
                                                                                                                                      longField("contentUpdatedDate_alt2"),
                                                                                                                                      keyword("module"),
                                                                                                                                      text("title")),
                                                                                                                           OLDEST_INDEX,
                                                                                                                           properties(keyword("contentId"),
                                                                                                                                      keyword("contentUpdatedDate_alt2"),
                                                                                                                                      keyword("module")))));

    StatisticFieldMapping contentId = mappings.get("contentId");
    assertEquals("keyword", contentId.getType(), "the explicitly pushed type wins over the dynamic text of newer indices");
    assertFalse(contentId.isHasKeywordSubField());
    assertFalse(contentId.isTypeConflict(), "text against one aggregatable type is a drift, not a conflict");

    StatisticFieldMapping alt2 = mappings.get("contentUpdatedDate_alt2");
    assertEquals("long", alt2.getType(), "two aggregatable types: the most recent index wins");
    assertTrue(alt2.isTypeConflict(), "keyword against long across indices cannot be aggregated on the bare field");

    StatisticFieldMapping module = mappings.get("module");
    assertEquals("keyword", module.getType());
    assertFalse(module.isTypeConflict());

    StatisticFieldMapping title = mappings.get("title");
    assertEquals("text", title.getType());
    assertTrue(title.isHasKeywordSubField());
    assertFalse(title.isTypeConflict());

    assertNotNull(mappings.get("doc['timestamp'].value.year"), "scripted date sub-fields are still added");

    elasticsearchAnalyticsService.storeFieldsMappings();
    verify(settingService).set(any(), any(), any(), any(SettingValue.class));
  }

  @Test
  void mergeIndicesMappingsFlagsANewestTextFieldOnlyWhenAggregatableTypesDisagree() {
    StatisticFieldMapping field = byName(elasticsearchAnalyticsService.mergeIndicesMappings(indicesMapping(
                                                                                                           OLDEST_INDEX,
                                                                                                           properties(keyword("field")),
                                                                                                           MIDDLE_INDEX,
                                                                                                           properties(longField("field")),
                                                                                                           NEWEST_INDEX,
                                                                                                           properties(text("field"))))).get("field");

    assertEquals("text", field.getType(), "no single explicit type to restore: the most recent index wins");
    assertTrue(field.isHasKeywordSubField());
    assertTrue(field.isTypeConflict());
  }

  /**
   * EXO-90504, the incident's shape: after a rollover the write index mapped a
   * profile property as {@code long} while the older indices hold it as
   * {@code keyword}, and no other sub-field differs. Guards the newest-wins
   * rule of EXO-90171 against the nested case that the previous merge (a
   * descent gated on new sub-keys) got wrong; it passed on its first run.
   */
  @Test
  void mergeIndicesMappingsTakesTheNewestTypeOfANestedPropertyWhenTheSubKeysAreIdentical() {
    Map<String, StatisticFieldMapping> mappings = byName(elasticsearchAnalyticsService.mergeIndicesMappings(indicesMapping(
                                                                                                                           NEWEST_INDEX,
                                                                                                                           nested("profileProperties",
                                                                                                                                  longField("country"),
                                                                                                                                  keyword("city")),
                                                                                                                           OLDEST_INDEX,
                                                                                                                           nested("profileProperties",
                                                                                                                                  keyword("country"),
                                                                                                                                  keyword("city")))));

    StatisticFieldMapping country = mappings.get("profileProperties.country");
    assertEquals("long", country.getType(), "the write index's type must be seen, or its refusals are never explained");
    assertTrue(country.isTypeConflict());
    assertEquals("keyword", mappings.get("profileProperties.city").getType());
    assertFalse(mappings.get("profileProperties.city").isTypeConflict());
  }

  private static String nested(String name, String... fields) {
    return "\"" + name + "\":{\"properties\":{" + properties(fields) + "}}";
  }

  private static Map<String, StatisticFieldMapping> byName(Set<StatisticFieldMapping> mappings) {
    return mappings.stream().collect(Collectors.toMap(StatisticFieldMapping::getName, Function.identity()));
  }

  private static String indicesMapping(Object... indexNameAndProperties) {
    StringBuilder json = new StringBuilder("{");
    for (int i = 0; i < indexNameAndProperties.length; i += 2) {
      if (i > 0) {
        json.append(",");
      }
      json.append("\"")
          .append(indexNameAndProperties[i])
          .append("\":{\"mappings\":{\"properties\":{")
          .append(indexNameAndProperties[i + 1])
          .append("}}}");
    }
    return json.append("}").toString();
  }

  private static String properties(String... fields) {
    return String.join(",", fields);
  }

  private static String keyword(String name) {
    return "\"" + name + "\":{\"type\":\"keyword\"}";
  }

  private static String longField(String name) {
    return "\"" + name + "\":{\"type\":\"long\"}";
  }

  private static String text(String name) {
    return "\"" + name + "\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}}";
  }

}
