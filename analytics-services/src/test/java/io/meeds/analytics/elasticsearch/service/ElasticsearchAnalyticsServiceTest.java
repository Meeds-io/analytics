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
package io.meeds.analytics.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.chart.ChartData;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * Unit tests for the GROUP_BY (threshold aggregation) support added in
 * {@link ElasticsearchAnalyticsService}: the generated Elasticsearch query
 * and the parsing of its response.
 */
@ExtendWith(MockitoExtension.class)
public class ElasticsearchAnalyticsServiceTest {

  @Mock
  private ElasticsearchAnalyticsStorage elasticsearchStorage;

  private ElasticsearchAnalyticsService elasticsearchAnalyticsService;

  @BeforeEach
  public void setUp() {
    elasticsearchAnalyticsService = new ElasticsearchAnalyticsService();
    ReflectionTestUtils.setField(elasticsearchAnalyticsService, "elasticsearchStorage", elasticsearchStorage);
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
  public void testGroupByAggregationGeneratesExpectedQuery() {
    when(elasticsearchStorage.search(anyString())).thenReturn(cannedResponse(2));

    AnalyticsFilter filter = newGroupByFilter(5);
    ChartDataList chartDataList = elasticsearchAnalyticsService.computeChartData(filter);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(elasticsearchStorage).search(queryCaptor.capture());
    String generatedQuery = queryCaptor.getValue();

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
  public void testGroupByThresholdZeroIsFlooredToOne() {
    when(elasticsearchStorage.search(anyString())).thenReturn(cannedResponse(4));

    // A threshold of 0 (or unset/negative) must never be sent as-is to ES:
    // it would make ES return every distinct value with zero occurrences,
    // which is both meaningless and costly on a large dataset.
    AnalyticsFilter filter = newGroupByFilter(0);
    elasticsearchAnalyticsService.computeChartData(filter);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(elasticsearchStorage).search(queryCaptor.capture());
    String generatedQuery = queryCaptor.getValue();

    assertTrue(generatedQuery.contains("\"min_doc_count\": 1"),
              "A minDocCount <= 0 must be floored to 1, never sent as 0 or omitted");
    assertTrue(!generatedQuery.contains("\"min_doc_count\": 0"),
              "min_doc_count must never be 0 (would scan every empty bucket)");
  }

  @Test
  public void testGroupByIsNotUsedToSortAPrecedingTermsAggregation() {
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

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(elasticsearchStorage).search(queryCaptor.capture());
    String generatedQuery = queryCaptor.getValue();

    assertTrue(!generatedQuery.contains("\"order\": {\"aggregation_result_value"),
              "Terms aggregation must not be ordered by the GROUP_BY pipeline aggregation");
  }

}
