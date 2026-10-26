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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticFieldMapping;
import io.meeds.analytics.model.StatisticFieldValue;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.collaborative.CollaborativeActivityBrowserOperation;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDeclaration;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDefinition;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import io.meeds.analytics.service.CollaborativeActivityServiceImpl;
import io.meeds.analytics.utils.AnalyticsUtils;

/**
 * The virtual collaborative-activity field in {@link ElasticsearchAnalyticsService}:
 * the translation of a filter on it, the scripted-entry filters, the value
 * list, the per-sample value, the keyword rule a page parameter must pass,
 * the published mapping view and the failed-shards count. The request body
 * is parsed, not matched as text, so that a misplaced brace is caught here;
 * what the engine does with the composed query is the IT's job.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchAnalyticsServiceCollaborativeTest {

  private static final String           FIELD              = CollaborativeActivityService.FIELD_NAME;

  private static final String           YES                = CollaborativeActivityService.YES;

  private static final String           SCRIPTED_ENTRY     = "doc['timestamp'].value.dayOfWeek";

  private static final ObjectMapper     STRICT_PARSER      = new ObjectMapper();

  private static final String           EMPTY_SEARCH       = """
      {"took": 1, "_shards": {"total": 2, "successful": 2, "failed": 0},
       "hits": {"total": {"value": 0}, "hits": []}}
      """;

  @Mock
  private SettingService                settingService;

  @Mock
  private ElasticsearchAnalyticsStorage elasticsearchStorage;

  private ElasticsearchAnalyticsService service;

  private CollaborativeActivityServiceImpl collaborativeActivityService;

  @BeforeEach
  void setUp() {
    collaborativeActivityService = new CollaborativeActivityServiceImpl();
    CollaborativeActivityDeclaration stream = new CollaborativeActivityDeclaration();
    stream.setOperations(List.of("createActivity", "createComment"));
    CollaborativeActivityDeclaration others = new CollaborativeActivityDeclaration();
    others.setOperations(List.of("taskCreated"));
    others.setLegacyOperations(List.of("noteCreated"));
    others.setExcludedActivityTypes(List.of("ks-wiki:spaces", "news"));
    others.setBrowserOperations(List.of(new CollaborativeActivityBrowserOperation("sendMessage", "Chat")));
    ReflectionTestUtils.setField(collaborativeActivityService,
                                 "definition",
                                 new CollaborativeActivityDefinition(List.of(stream, others), List.of(), List.of(), List.of()));
    service = new ElasticsearchAnalyticsService();
    ReflectionTestUtils.setField(service, "elasticsearchStorage", elasticsearchStorage);
    ReflectionTestUtils.setField(service, "settingService", settingService);
    ReflectionTestUtils.setField(service, "collaborativeActivityService", collaborativeActivityService);
    ReflectionTestUtils.setField(service, "aggregationReturnedDocumentsSize", 200);
    when(elasticsearchStorage.getMappedFieldNames()).thenReturn(Map.of());
    when(elasticsearchStorage.search(anyString())).thenReturn(EMPTY_SEARCH);
  }

  @Test
  void aFilterOnTheVirtualFieldBecomesTheClauseInsideMustAndNeverNamesTheField() {
    service.retrieveData(List.of(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.EQUAL, YES)), 0, 10, null);

    JSONArray must = mustOf(captureQuery());
    assertEquals(1, must.length(), must.toString());
    assertEquals(1, must.getJSONObject(0).getJSONObject("bool").getInt("minimum_should_match"), "the definition's clause");
    assertFalse(captureQuery().contains(FIELD), "no document carries the field, so the request never names it");
    assertFalse(captureQuery().contains("script"));
  }

  @Test
  void eachFilterTypeOnTheVirtualFieldIsEitherTheClauseItsNegationNothingOrNothingMatches() {
    assertClause(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.EQUAL, YES), false);
    assertClause(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.IN_SET, "YES"), false);
    assertClause(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.NOT_NULL), false);
    assertClause(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.NOT_EQUAL, YES), true);
    assertClause(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.NOT_IN_SET, "YES"), true);
    assertClause(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.IS_NULL), true);

    service.retrieveData(List.of(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.EQUAL, "NO")), 0, 10, null);
    JSONArray must = mustOf(captureQuery());
    assertEquals(1, must.length());
    assertTrue(must.getJSONObject(0).has("match_none"), "the field has no other value: a positive filter on NO matches nothing");

    service.retrieveData(List.of(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.NOT_EQUAL, "NO")), 0, 10, null);
    assertEquals(0, mustOf(captureQuery()).length(), "and its negation matches everything, so no condition at all");
  }

  @Test
  void anAlternativeActivityTypeNameInTheViewIsHandedToTheClause() {
    setView(Map.of("activityType", keyword("activityType"), "activityType_alt", keyword("activityType_alt")));

    service.retrieveData(List.of(new AnalyticsFieldFilter(FIELD, AnalyticsFieldFilterType.EQUAL, YES)), 0, 10, null);

    String query = captureQuery();
    assertTrue(query.contains("\"activityType_alt\""), query);
    assertTrue(query.contains("\"activityType_alt.keyword\""), query);
  }

  @Test
  void aFilterOnAScriptedEntryMatchesNothingOrEverythingExplicitly() {
    service.mergeIndicesMappings(indicesMapping("analytics_2026-10-01", keyword("module")));
    assertNotNull(publishedView().get(SCRIPTED_ENTRY), "precondition: the view holds the scripted entry");

    service.retrieveData(List.of(new AnalyticsFieldFilter(SCRIPTED_ENTRY, AnalyticsFieldFilterType.EQUAL, "2")), 0, 10, null);
    JSONArray must = mustOf(captureQuery());
    assertEquals(1, must.length());
    assertTrue(must.getJSONObject(0).has("match_none"), must.toString());
    assertFalse(captureQuery().contains("dayOfWeek"), "the script's name is never written as a field name");

    service.retrieveData(List.of(new AnalyticsFieldFilter(SCRIPTED_ENTRY, AnalyticsFieldFilterType.NOT_EQUAL, "2")), 0, 10, null);
    assertEquals(0, mustOf(captureQuery()).length(), "a negated filter on it emits nothing");
    assertFalse(captureQuery().contains("dayOfWeek"));
  }

  @Test
  void collaborativeOnlyAddsTheClauseToSamplesAndToChart() {
    service.getSamples(null, "spaceId", List.of("42"), "timestamp", "desc", 1, true);
    String samples = captureQuery();
    assertTrue(samples.contains("minimum_should_match"), samples);
    assertTrue(samples.contains("\"spaceId\""), "the other filter stays");

    service.getSamples(null, "spaceId", List.of("42"), "timestamp", "desc", 1, false);
    assertFalse(captureQuery().contains("minimum_should_match"), "without the option nothing changes");

    when(elasticsearchStorage.search(anyString())).thenReturn(chartResponse(0));
    service.getChart(null, null, null, "spaceId", AnalyticsAggregationType.TERMS, "desc", "timestamp",
                     AnalyticsAggregationType.MAX, "desc", 10, true);
    assertTrue(captureQuery().contains("minimum_should_match"));
  }

  /**
   * The view the processor hands to the storage at a rollover is what
   * {@code retrieveMapping} returns: the virtual field must never be in it,
   * or the next weekly index would map it and the field would stop being
   * virtual. Pinned on the merged view and on the stored one.
   */
  @Test
  void theMappingViewNeverHoldsTheVirtualField() {
    when(settingService.get(any(), any(), any())).thenAnswer(invocation -> SettingValue.create("""
        {"module": {"name": "module", "type": "keyword", "hasKeywordSubField": false,
                    "scriptedField": false, "typeConflict": false}}
        """));
    service.readFieldsMapping();
    java.util.Set<StatisticFieldMapping> merged = service.mergeIndicesMappings(indicesMapping("analytics_2026-10-01",
                                                                                              keyword("operation")));

    assertTrue(merged.stream().noneMatch(field -> FIELD.equals(field.getName())), merged.toString());
    assertFalse(publishedView().containsKey(FIELD));
    assertTrue(service.retrieveFieldValues(FIELD, 1).stream().anyMatch(value -> YES.equals(value.getValue())),
               "the field exists for the drawer through its values, never through the view");
  }

  @Test
  void theOneValueOfTheVirtualFieldIsAnsweredWithoutAQuery() {
    List<StatisticFieldValue> values = service.retrieveFieldValues(FIELD, 10);

    assertEquals(1, values.size());
    assertEquals(YES, values.getFirst().getValue());
    verify(elasticsearchStorage, never()).search(anyString());
  }

  @Test
  void aSampleReadBackCarriesTheVirtualFieldByTheRuleOnItsOwnKeys() {
    when(elasticsearchStorage.search(anyString())).thenReturn("""
        {"took": 1, "hits": {"total": {"value": 5}, "hits": [
          {"_source": %s},
          {"_source": %s},
          {"_source": %s},
          {"_source": %s},
          {"_source": %s}
        ]}}
        """.formatted(source("taskCreated", "\"watcher\": \"x\""),
                      source("createActivity", "\"statisticOrigin\": \"websocket\""),
                      source("sendMessage", "\"statisticOrigin\": \"websocket\", \"watcher\": \"chat\""),
                      source("createActivity", "\"activityType_alt\": \"ks-wiki:spaces\""),
                      source("createComment", "\"activityType\": \"\", \"activityType_alt2\": \"files:spaces\"")));

    List<StatisticData> samples = service.retrieveData(List.of(), 0, 10, null);

    assertEquals(5, samples.size());
    assertEquals(YES, samples.get(0).getKeyword(FIELD), "a declared server operation");
    assertNull(samples.get(1).getKeyword(FIELD), "a post forged from a page shows no value");
    assertEquals("websocket", samples.get(1).getKeyword(AnalyticsUtils.STATISTIC_ORIGIN), "the origin is read back as a parameter");
    assertEquals(YES, samples.get(2).getKeyword(FIELD), "a declared browser operation");
    assertNull(samples.get(3).getKeyword(FIELD), "the type is read under the alternative key the document holds");
    assertEquals(YES, samples.get(4).getKeyword(FIELD), "an empty base key is skipped for the first non-empty variant");
  }

  @Test
  void isKeywordFieldClosesEveryFieldAServerWriterTypedWhateverVariantIsLatest() {
    setView(Map.of("module", keyword("module"),
                   "title", new StatisticFieldMapping("title", "text", true),
                   "pageLoadMillis", longField("pageLoadMillis"),
                   "count", longField("count"),
                   "count_alt", keyword("count_alt"),
                   "deviceType", keyword("deviceType")));
    when(elasticsearchStorage.getMappedFieldNames()).thenReturn(Map.of("deviceType_alt", "long", "isPwa", "keyword"));

    assertTrue(service.isKeywordField("unknownParameter"), "an unknown field is kept as a keyword, as today");
    assertTrue(service.isKeywordField("module"));
    assertTrue(service.isKeywordField("title"), "text is what dynamic mapping gave string parameters before the explicit mappings");
    assertTrue(service.isKeywordField("isPwa"), "a keyword the storage created since the last refresh");
    assertFalse(service.isKeywordField("pageLoadMillis"), "a long typed by a server writer");
    assertFalse(service.isKeywordField("count"), "a long base with a later keyword variant stays closed");
    assertFalse(service.isKeywordField("count_alt"), "the variant name leads to the same field");
    assertFalse(service.isKeywordField("deviceType"), "a keyword whose non-keyword variant was created since the last refresh");
    assertFalse(service.isKeywordField(" "));
    assertFalse(service.isKeywordField(null));
  }

  @Test
  void failedShardsIsReadFromTheAnswer() {
    when(elasticsearchStorage.search(anyString())).thenReturn(chartResponse(2));
    ChartDataList withFailure = service.computeChartData(chartFilter());
    assertEquals(2, withFailure.getFailedShards());

    when(elasticsearchStorage.search(anyString())).thenReturn(chartResponse(0).replace("\"_shards\": {\"total\": 2, \"successful\": 0, \"failed\": 0},", ""));
    ChartDataList withoutShards = service.computeChartData(chartFilter());
    assertEquals(0, withoutShards.getFailedShards(), "an answer with no _shards block is a complete one");
  }

  /**
   * The stored view keeps the entries no live index holds any more (a field of
   * an index since deleted); a refresh that rebuilt the view from the engine's
   * answer alone would drop them and then save the shrunken view. The mutant
   * that rebuilds from the answer alone fails here.
   */
  @Test
  void aStoredEntryNoLiveIndexHoldsSurvivesARefreshAndIsSavedAgain() {
    when(settingService.get(any(), any(), any())).thenAnswer(invocation -> SettingValue.create("""
        {"legacyField": {"name": "legacyField", "type": "keyword", "hasKeywordSubField": false,
                         "scriptedField": false, "typeConflict": false}}
        """));
    service.readFieldsMapping();
    Object viewBefore = ReflectionTestUtils.getField(service, "esMappings");
    assertNotNull(publishedView().get("legacyField"), "precondition: the stored entry is in the view");

    service.mergeIndicesMappings(indicesMapping("analytics_2026-10-01", keyword("module")));

    Map<String, StatisticFieldMapping> view = publishedView();
    assertNotNull(view.get("legacyField"), "the view never shrinks");
    assertNotNull(view.get("module"));
    assertNotSame(viewBefore, ReflectionTestUtils.getField(service, "esMappings"), "a refresh publishes a new map");
    @SuppressWarnings("unchecked")
    Map<String, StatisticFieldMapping> published = (Map<String, StatisticFieldMapping>) ReflectionTestUtils.getField(service,
                                                                                                                     "esMappings");
    assertThrows(UnsupportedOperationException.class, () -> published.put("x", keyword("x")), "the published view is immutable");

    assertDoesNotThrow(() -> service.storeFieldsMappings());
    ArgumentCaptor<SettingValue<?>> saved = ArgumentCaptor.forClass(SettingValue.class);
    verify(settingService).set(any(), any(), any(), saved.capture());
    assertTrue(saved.getValue().getValue().toString().contains("legacyField"), "the saved view still carries it");
  }

  private void assertClause(AnalyticsFieldFilter filter, boolean negated) {
    service.retrieveData(List.of(filter), 0, 10, null);
    JSONArray must = mustOf(captureQuery());
    assertEquals(1, must.length(), filter.getType() + ": " + must);
    JSONObject condition = must.getJSONObject(0);
    if (negated) {
      condition = condition.getJSONObject("bool").getJSONArray("must_not").getJSONObject(0);
    }
    assertEquals(1, condition.getJSONObject("bool").getInt("minimum_should_match"), filter.getType().toString());
  }

  private JSONArray mustOf(String query) {
    return new JSONObject(query).getJSONObject("query").getJSONObject("bool").getJSONArray("must");
  }

  /**
   * The body is checked by a strict parser: org.json accepts a trailing comma
   * in a list or an object, which Elasticsearch refuses, and the clause is
   * appended into a hand-built list with its own separators.
   */
  private String captureQuery() {
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(elasticsearchStorage, org.mockito.Mockito.atLeastOnce()).search(queryCaptor.capture());
    String generatedQuery = queryCaptor.getValue();
    assertDoesNotThrow(() -> STRICT_PARSER.readTree(generatedQuery), () -> "not well-formed JSON:\n" + generatedQuery);
    return generatedQuery;
  }

  private void setView(Map<String, StatisticFieldMapping> view) {
    ReflectionTestUtils.setField(service, "esMappings", new HashMap<>(view));
  }

  private AnalyticsFilter chartFilter() {
    AnalyticsFilter filter = new AnalyticsFilter();
    filter.setFilters(new ArrayList<>());
    filter.addXAxisAggregation(new AnalyticsAggregation(AnalyticsAggregationType.TERMS, "spaceId", "desc", null, 10));
    filter.setYAxisAggregation(new AnalyticsAggregation(AnalyticsAggregationType.MAX, "timestamp", "desc", null, 1));
    return filter;
  }

  private static String chartResponse(int failedShards) {
    return """
        {"took": 5,
         "_shards": {"total": 2, "successful": %d, "failed": %d},
         "hits": {"total": {"value": 42}},
         "aggregations": {"aggregation_result": {"buckets": [
           {"key": 42, "doc_count": 10, "aggregation_result_value": {"value": 1700000000000}}
         ]}}}
        """.formatted(2 - failedShards, failedShards);
  }

  private static String source(String operation, String extraFields) {
    return """
        {"id": 1, "timestamp": 1700000000000, "userId": 1, "spaceId": 0, "module": "m", "subModule": "s",
         "operation": "%s", "status": 0, "errorCode": 0, "duration": 0, "isAnalytics": true, %s}
        """.formatted(operation, extraFields);
  }

  private static StatisticFieldMapping keyword(String name) {
    return new StatisticFieldMapping(name, "keyword", false);
  }

  private static StatisticFieldMapping longField(String name) {
    return new StatisticFieldMapping(name, "long", false);
  }

  /**
   * The published view, read from the field: {@code retrieveMapping} is the
   * container-transactional wrapper, whose aspect would boot a Kernel here.
   */
  @SuppressWarnings("unchecked")
  private Map<String, StatisticFieldMapping> publishedView() {
    return (Map<String, StatisticFieldMapping>) ReflectionTestUtils.getField(service, "esMappings");
  }

  private static String indicesMapping(String indexName, StatisticFieldMapping... fields) {
    StringBuilder properties = new StringBuilder();
    for (StatisticFieldMapping field : fields) {
      if (!properties.isEmpty()) {
        properties.append(',');
      }
      properties.append("\"").append(field.getName()).append("\":{\"type\":\"").append(field.getType()).append("\"}");
    }
    return "{\"" + indexName + "\":{\"mappings\":{\"properties\":{" + properties + "}}}}";
  }

}
