/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.elasticsearch.model.ElasticsearchMappingConflictException;
import io.meeds.analytics.elasticsearch.model.ElasticsearchResponse;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.model.StatisticFieldMapping;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchAnalyticsStorageTest {

  private static final String           ES_URL            = "http://es:9200";

  private static final String           INDEX_PREFIX      = "analytics";

  private static final String           INDEX_ALIAS       = "analytics_alias";

  private static final String           TEMPLATE_NAME     = "analytics_template";

  private static final String           TEMPLATE          = """
      {"index_patterns": ["analytics*"], "template": {"mappings": {"properties": {
        "timestamp": {"type": "date", "format": "epoch_millis"},
        "module": {"type": "keyword"},
        "errorMessage": {"type": "text", "index_options": "offsets"}
      }}}}
      """;

  @Mock
  private HttpClient                    httpClient;

  @Mock
  private ElasticsearchConfiguration    configuration;

  @Mock
  private ListenerService               listenerService;

  @InjectMocks
  private ElasticsearchAnalyticsStorage storage;

  private final List<String>            createIndexBodies = new ArrayList<>();

  private final List<String>            templateBodies    = new ArrayList<>();

  private boolean                       indexExists;

  private boolean                       templateExists    = true;

  private int                           templateReads;

  private int                           createIndexFailures;

  private String                        bulkResponse      = "{\"errors\":false,\"items\":[]}";

  @BeforeEach
  void setUp() throws Exception {
    when(configuration.getUrlClient()).thenReturn(ES_URL);
    when(configuration.getIndexPrefix()).thenReturn(INDEX_PREFIX);
    when(configuration.getIndexAlias()).thenReturn(INDEX_ALIAS);
    when(configuration.getIndexTemplateName()).thenReturn(TEMPLATE_NAME);
    when(configuration.getIndexPerDays()).thenReturn(7L);
    when(configuration.getMaxIndexCount()).thenReturn(500L);
    when(configuration.getIndexTemplateMapping()).thenReturn(TEMPLATE);
    when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                                                                                                  .thenAnswer(invocation -> answer(invocation.getArgument(0)));
  }

  @Test
  void aNewWeeklyIndexIsCreatedWithTheKnownFieldMappingsInOneRequest() {
    storage.sendCreateBulkDocumentsRequest(List.of(new StatisticDataQueueEntry(statisticData())), knownMappings());

    assertEquals(1, createIndexBodies.size());
    JSONObject body = new JSONObject(createIndexBodies.get(0));
    assertTrue(body.getJSONObject("aliases").getJSONObject(INDEX_ALIAS).getBoolean("is_write_index"));
    JSONObject properties = body.getJSONObject("mappings").getJSONObject("properties");
    assertEquals("keyword", properties.getJSONObject("contentId").getString("type"));
    assertEquals("long", properties.getJSONObject("contentUpdatedDate").getString("type"));
    assertEquals("long", properties.getJSONObject("contentId_alt2").getString("type"));
    assertEquals("float", properties.getJSONObject("score").getString("type"));
    JSONObject title = properties.getJSONObject("contentTitle");
    assertEquals("text", title.getString("type"));
    assertEquals(256, title.getJSONObject("fields").getJSONObject("keyword").getInt("ignore_above"));
    assertFalse(properties.getJSONObject("description").has("fields"), "a text field without keyword sub-field stays plain");
    assertFalse(properties.has("module"), "template fields are left to the template");
    assertFalse(properties.has("profileProperties.city"), "nested properties are left to dynamic mapping");
    assertFalse(properties.has("doc['timestamp'].value.year"), "scripted fields are not mappings");
    assertFalse(properties.has("someDate"), "a date needs its format, left to dynamic mapping");
    assertFalse(properties.has("count"), "types the storage never pushes are not copied from ES");
    assertTrue(indexExists);
  }

  @Test
  void aRefusedMappingBodyFallsBackToAPlainIndexCreation() {
    createIndexFailures = 1;

    storage.sendCreateBulkDocumentsRequest(List.of(new StatisticDataQueueEntry(statisticData())), knownMappings());

    assertEquals(2, createIndexBodies.size());
    assertTrue(new JSONObject(createIndexBodies.get(0)).has("mappings"));
    JSONObject plain = new JSONObject(createIndexBodies.get(1));
    assertFalse(plain.has("mappings"));
    assertTrue(plain.getJSONObject("aliases").getJSONObject(INDEX_ALIAS).getBoolean("is_write_index"));
    assertTrue(indexExists);
  }

  @Test
  void anExistingWeeklyIndexIsNotCreatedAgain() {
    indexExists = true;

    storage.sendCreateBulkDocumentsRequest(List.of(new StatisticDataQueueEntry(statisticData())), knownMappings());

    assertTrue(createIndexBodies.isEmpty());
  }

  /**
   * EXO-90504: a cluster keeps the template it was provisioned with, so the
   * {@code numeric_detection} switch of MEED-9542 never reached it. The
   * template is pushed at every startup, whether or not it exists.
   */
  @Test
  void theIndexTemplateIsPushedAtStartupEvenWhenItAlreadyExists() {
    templateExists = true;

    storage.init();

    assertEquals(1, templateBodies.size(), "an existing template is updated, not left as is");
    assertEquals(new JSONObject(TEMPLATE).toString(), new JSONObject(templateBodies.get(0)).toString());
    assertEquals(1, templateReads, "an existing template is not read back after the update");
  }

  @Test
  void theIndexTemplateIsCreatedWhenMissing() {
    templateExists = false;

    storage.init();

    assertEquals(1, templateBodies.size());
    assertEquals(2, templateReads, "the creation is verified by reading the template back");
  }

  @Test
  void aBulkRefusedForAFieldTypeConflictRaisesAMappingConflictExceptionCarryingTheRefusedDocuments() {
    indexExists = true;
    bulkResponse = """
        {"errors":true,"items":[
          {"create":{"_index":"analytics_2026-09-17","_id":"1","status":400,"error":{"type":"document_parsing_exception",
            "reason":"failed to parse field [profileProperties.country] of type [long]"}}},
          {"create":{"_index":"analytics_2026-09-17","_id":"2","status":201,"result":"created"}},
          {"create":{"_index":"analytics_2026-09-17","_id":"3","status":409,"error":{"type":"version_conflict_engine_exception",
            "reason":"[3]: version conflict, document already exists (current version [1])"}}}]}
        """;
    List<StatisticDataQueueEntry> entries = List.of(new StatisticDataQueueEntry(statisticData()));

    ElasticsearchMappingConflictException thrown = assertThrows(ElasticsearchMappingConflictException.class,
                                                                () -> storage.sendCreateBulkDocumentsRequest(entries,
                                                                                                             knownMappings()));

    assertEquals(Set.of("1"), thrown.getRefusedDocumentIds(), "only the refused document, not the created or the duplicate one");
    assertEquals(List.of("failed to parse field [profileProperties.country] of type [long]"), thrown.getReasons());
  }

  @Test
  void aBulkWhoseOnlyErrorsAreVersionConflictsIsNotAnError() {
    indexExists = true;
    bulkResponse = """
        {"errors":true,"items":[
          {"create":{"_index":"analytics_2026-09-17","_id":"1","status":409,"error":{"type":"version_conflict_engine_exception",
            "reason":"[1]: version conflict, document already exists (current version [1])"}}},
          {"create":{"_index":"analytics_2026-09-17","_id":"2","status":409,"error":{"type":"version_conflict_engine_exception",
            "reason":"[2]: version conflict, document already exists (current version [1])"}}},
          {"create":{"_index":"analytics_2026-09-17","_id":"3","status":201,"result":"created"}}]}
        """;
    List<StatisticDataQueueEntry> entries = List.of(new StatisticDataQueueEntry(statisticData()),
                                                    new StatisticDataQueueEntry(statisticData()));

    assertDoesNotThrow(() -> storage.sendCreateBulkDocumentsRequest(entries, knownMappings()),
                       "documents already indexed by a previous attempt are not a failure of this one");
  }

  @Test
  void aBulkWithAnotherErrorStaysAGenericError() {
    indexExists = true;
    bulkResponse = """
        {"errors":true,"items":[
          {"create":{"_index":"analytics_2026-09-17","_id":"1","status":429,"error":{"type":"es_rejected_execution_exception",
            "reason":"rejected execution"}}}]}
        """;
    List<StatisticDataQueueEntry> entries = List.of(new StatisticDataQueueEntry(statisticData()));

    IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                () -> storage.sendCreateBulkDocumentsRequest(entries, knownMappings()));
    assertFalse(thrown instanceof ElasticsearchMappingConflictException, "only a type conflict asks for a mapping refresh");
  }

  private ElasticsearchResponse answer(ClassicHttpRequest request) throws Exception {
    String path = request.getUri().getPath();
    String method = request.getMethod();
    boolean weeklyIndexPath = path.matches("/" + INDEX_PREFIX + "_\\d{4}-\\d{2}-\\d{2}");
    if (path.equals("/_index_template/" + TEMPLATE_NAME)) {
      if ("GET".equals(method)) {
        templateReads++;
        return templateExists ? ok("{}") : new ElasticsearchResponse("{\"status\":404}", 404);
      }
      templateBodies.add(EntityUtils.toString(request.getEntity()));
      templateExists = true;
      return ok("{\"acknowledged\":true}");
    } else if (path.equals("/_bulk")) {
      return ok(bulkResponse);
    } else if ("GET".equals(method) && weeklyIndexPath) {
      return indexExists ? ok("{}") : new ElasticsearchResponse("{\"status\":404}", 404);
    } else if ("GET".equals(method) && path.equals("/" + INDEX_ALIAS)) {
      return new ElasticsearchResponse("{\"status\":404}", 404);
    } else if ("PUT".equals(method) && weeklyIndexPath) {
      createIndexBodies.add(EntityUtils.toString(request.getEntity()));
      if (createIndexFailures-- > 0) {
        return new ElasticsearchResponse("{\"error\":{\"type\":\"mapper_parsing_exception\"},\"status\":400}", 400);
      }
      indexExists = true;
      return ok("{\"acknowledged\":true}");
    }
    return ok("{}");
  }

  private static ElasticsearchResponse ok(String body) {
    return new ElasticsearchResponse(body, 200);
  }

  private static StatisticData statisticData() {
    StatisticData data = new StatisticData();
    data.setModule("test");
    data.setSubModule("test");
    data.setOperation("test");
    data.setTimestamp(System.currentTimeMillis());
    data.setUserId(1);
    return data;
  }

  private static Set<StatisticFieldMapping> knownMappings() {
    Set<StatisticFieldMapping> mappings = new HashSet<>();
    mappings.add(new StatisticFieldMapping("contentId", "keyword", false));
    mappings.add(new StatisticFieldMapping("contentUpdatedDate", "long", false));
    mappings.add(new StatisticFieldMapping("contentId_alt2", "long", false));
    mappings.add(new StatisticFieldMapping("score", "float", false));
    mappings.add(new StatisticFieldMapping("contentTitle", "text", true));
    mappings.add(new StatisticFieldMapping("description", "text", false));
    mappings.add(new StatisticFieldMapping("module", "keyword", false));
    mappings.add(new StatisticFieldMapping("profileProperties.city", "text", true));
    mappings.add(new StatisticFieldMapping("doc['timestamp'].value.year", "long", false, true));
    mappings.add(new StatisticFieldMapping("someDate", "date", false));
    mappings.add(new StatisticFieldMapping("count", "integer", false));
    return mappings;
  }
}
