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
package io.meeds.analytics.elasticsearch.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

import org.exoplatform.services.listener.ListenerService;

import io.meeds.analytics.elasticsearch.AbstractElasticsearchIT;
import io.meeds.analytics.elasticsearch.model.AnalyticsMigrationRunningException;
import io.meeds.analytics.elasticsearch.model.ElasticsearchResponse;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchConfiguration;

/**
 * A server whose statistics started before the index template typed
 * {@code spaceId} and {@code userId} as {@code keyword} holds them as
 * {@code long} in its older weekly indices and as {@code keyword} in the newer
 * ones. Executed against a real engine, with an index template that gives its
 * alias to every index named after the prefix, as the analytics one does: the
 * plain grouping reads the indices of one type only, and reads every week once
 * the older indices are migrated.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsIndexTypeMigrationServiceIT extends AbstractElasticsearchIT {

  private static final String                PREFIX       = "it_analytics_migration";

  private static final String                ALIAS        = PREFIX + "_alias";

  private static final String                TEMPLATE     = PREFIX + "_template";

  private static final String                OLDEST_INDEX = PREFIX + "_2020-01-02";

  private static final String                OLD_INDEX    = PREFIX + "_2020-01-09";

  private static final String                NEWEST_INDEX = PREFIX + "_2020-01-16";

  private static final String                NEXT_INDEX   = PREFIX + "_2020-01-23";

  private static final String                LOCK         = AnalyticsIndexTypeMigrationService.MIGRATION_INDEX_PREFIX + PREFIX + "-lock";

  /** Behind the alias through the template, and no week */
  private static final String                OTHER_INDEX  = PREFIX + "_zzz";

  private static final String                COPIES       = AnalyticsIndexTypeMigrationService.MIGRATION_INDEX_PREFIX + PREFIX + "_*";

  private static final String                OLD_COPY     = AnalyticsIndexTypeMigrationService.MIGRATION_INDEX_PREFIX + OLD_INDEX;

  private final HttpClient                   client       = HttpClient.newBuilder()
                                                                      .connectTimeout(Duration.ofSeconds(5))
                                                                      .build();

  @Mock
  private ElasticsearchConfiguration         configuration;

  @Mock
  private ListenerService                    listenerService;

  private ElasticsearchAnalyticsStorage      storage;

  private AnalyticsIndexTypeMigrationService migrationService;

  @BeforeEach
  void setUp() throws Exception {
    String url = elasticsearchUrl();
    when(configuration.getUrlClient()).thenReturn(url);
    when(configuration.getIndexPrefix()).thenReturn(PREFIX);
    when(configuration.getIndexAlias()).thenReturn(ALIAS);
    when(configuration.getIndexTemplateMapping()).thenReturn("""
        {"template":{"mappings":{"properties":{
          "timestamp":{"type":"date","format":"epoch_millis"},
          "userId":{"type":"keyword"},"spaceId":{"type":"keyword"},"operation":{"type":"keyword"}}}}}
        """);

    storage = spy(new ElasticsearchAnalyticsStorage());
    ReflectionTestUtils.setField(storage, "elasticsearchConfiguration", configuration);
    ReflectionTestUtils.setField(storage, "listenerService", listenerService);
    ReflectionTestUtils.setField(storage, "httpClient", HttpClientBuilder.create().build());

    migrationService = new AnalyticsIndexTypeMigrationService();
    ReflectionTestUtils.setField(migrationService, "storage", storage);
    ReflectionTestUtils.setField(migrationService, "configuration", configuration);
    ReflectionTestUtils.setField(migrationService, "taskPollPeriodMs", 50L);

    cleanCluster();
    // The older weeks hold the ids as numbers, the newest one as keywords
    createIndex(OLDEST_INDEX, "long", "long", false);
    createIndex(OLD_INDEX, "long", "long", false);
    index(OLDEST_INDEX, "1", event(1000, 1, 7, 15));
    index(OLD_INDEX, "2", event(2000, 2, 7, 15));
    index(OLD_INDEX, "3", event(3000, 1, 8, 15));
    // As the analytics template: its alias for every index named after the
    // prefix
    HttpResponse<String> template = send("PUT", "/_index_template/" + TEMPLATE, """
        {"index_patterns":["%s*"],"template":{"aliases":{"%s":{"is_write_index":true}}}}
        """.formatted(PREFIX, ALIAS));
    assertEquals(200, template.statusCode(), template::body);
    createIndex(NEWEST_INDEX, "keyword", "long", true);
    index(NEWEST_INDEX, "4", event(4000, "1", "7", 15));
    index(NEWEST_INDEX, "5", event(5000, "3", "8", 15));
    refresh();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanCluster();
  }

  @Test
  void theEngineAnswersAPlainGroupingFromTheIndicesOfOneTypeWithNoFailedShard() throws Exception {
    assertTrue(spacesOfThePlainGrouping() < 3, "the three spaces are not all listed by the plain request");
  }

  @Test
  void theEngineCountsAUserOfBothTypesMoreThanOnceWithNoFailedShard() throws Exception {
    assertEquals(4, usersOfThePlainDistinctCount(), "each of the two users is counted once per type by the plain request");
  }

  @Test
  void theOlderIndicesTakeTheTypesOfTheNewestOneAndKeepTheirNameAndDocuments() throws Exception {
    List<String> failedIndices = migrationService.migrate();

    assertEquals(List.of(), failedIndices);
    assertTrue(migrationService.isNewestIndexTypedAsTheTemplate());
    // The lock index the run created leaves no shard unassigned on one node
    HttpResponse<String> health = send("GET", "/_cluster/health/" + LOCK + "?wait_for_status=green&timeout=30s", null);
    assertEquals(200, health.statusCode(), health::body);
    assertEquals("green", new JSONObject(health.body()).getString("status"));
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    for (String index : List.of(OLDEST_INDEX, OLD_INDEX)) {
      assertEquals("keyword", fieldType(index, "spaceId"), index);
      assertEquals("keyword", fieldType(index, "userId"), index);
      assertEquals("long", fieldType(index, "rank"), index);
      assertFalse(indexSettings(index).has("refresh_interval"), "the migrated index is refreshed as any other");
    }
    refresh();
    assertEquals(1, count(OLDEST_INDEX));
    assertEquals(2, count(OLD_INDEX));
    assertEquals(5, count(ALIAS));
    assertEquals(3, spacesOfThePlainGrouping());
    assertEquals(2, usersOfThePlainDistinctCount());
    // A migrated index accepts writes, as any weekly index
    index(OLD_INDEX, "6", event(6000, 4, 8, 15));
    refresh();
    assertEquals(3, count(OLD_INDEX));
  }

  @Test
  void anIndexThatAlreadyHasTheTypesOfTheNewestOneIsLeftAsItIs() throws Exception {
    migrationService.migrate();
    String oldestIndexId = indexSettings(OLDEST_INDEX).getString("uuid");
    String oldIndexId = indexSettings(OLD_INDEX).getString("uuid");
    String newestIndexId = indexSettings(NEWEST_INDEX).getString("uuid");

    assertEquals(List.of(), migrationService.migrate());

    assertEquals(oldestIndexId, indexSettings(OLDEST_INDEX).getString("uuid"));
    assertEquals(oldIndexId, indexSettings(OLD_INDEX).getString("uuid"));
    assertEquals(newestIndexId, indexSettings(NEWEST_INDEX).getString("uuid"));
  }

  @Test
  void anUnfinishedCopyIsDeletedAndMadeAgain() throws Exception {
    // A run stopped while the copy was filling: it holds one of the two
    // documents
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    index(OLD_COPY, "2", event(2000, 2, 7, 15));
    refresh();
    assertEquals(5, count(ALIAS), "the copy that fills is not behind the alias");

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals("long", fieldType(OLD_INDEX, "rank"), "the copy made again has the mappings of the original");
    assertEquals(2, count(OLD_INDEX));
    assertEquals(5, count(ALIAS));
    assertEquals(3, spacesOfThePlainGrouping());
  }

  @Test
  void aCopyThatTookThePlaceOfItsOriginalTakesItsName() throws Exception {
    // A run stopped once the copy had the alias and the original was deleted
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"},"rank":{"type":"long"},
           "timestamp":{"type":"date","format":"epoch_millis"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    index(OLD_COPY, "2", event(2000, 2, 7, 15));
    index(OLD_COPY, "3", event(3000, 1, 8, 15));
    HttpResponse<String> swap = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}},{"remove_index":{"index":"%s"}}]}
        """.formatted(OLD_COPY, ALIAS, OLD_INDEX));
    assertEquals(200, swap.statusCode(), swap::body);
    refresh();

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(2, count(OLD_INDEX));
    assertEquals(5, count(ALIAS));
    assertEquals(3, spacesOfThePlainGrouping());
  }

  @Test
  void aCopyStillRunningInTheEngineIsCancelledBeforeItsCopyIsDeletedAndMadeAgain() throws Exception {
    // A run stopped while the engine was copying: the copy goes on, slowly
    for (int i = 10; i < 40; i++) {
      index(OLD_INDEX, String.valueOf(i), event(1000 + i, 2, 7, 15));
    }
    refresh();
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    HttpResponse<String> running = send("POST", "/_reindex?wait_for_completion=false&requests_per_second=1", """
        {"source":{"index":"%s","size":1},"dest":{"index":"%s"}}
        """.formatted(OLD_INDEX, OLD_COPY));
    assertEquals(200, running.statusCode(), running::body);
    assertEquals(1, runningCopies(), "the copy of the stopped run is running in the engine");

    assertEquals(List.of(), migrationService.migrate());

    assertEquals(0, runningCopies());
    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(32, count(OLD_INDEX));
    assertEquals(35, count(ALIAS));
  }

  @Test
  void anUnfinishedCloneIsDeletedAndMadeAgainFromTheCopyThatHasTheAlias() throws Exception {
    // A run stopped after the clone was asked: the copy has the alias, and an
    // index has the name of the original
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"},"rank":{"type":"long"},
           "timestamp":{"type":"date","format":"epoch_millis"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    index(OLD_COPY, "2", event(2000, 2, 7, 15));
    index(OLD_COPY, "3", event(3000, 1, 8, 15));
    HttpResponse<String> swap = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}},{"remove_index":{"index":"%s"}}]}
        """.formatted(OLD_COPY, ALIAS, OLD_INDEX));
    assertEquals(200, swap.statusCode(), swap::body);
    HttpResponse<String> clone = send("PUT", "/" + OLD_INDEX, """
        {"aliases":{"%s":{"is_write_index":false}},
         "settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"}}}}
        """.formatted(ALIAS));
    assertEquals(200, clone.statusCode(), clone::body);
    index(OLD_INDEX, "2", event(2000, 2, 7, 15));
    refresh();
    String unfinishedCloneId = indexSettings(OLD_INDEX).getString("uuid");

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertNotEquals(unfinishedCloneId, indexSettings(OLD_INDEX).getString("uuid"));
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals(2, count(OLD_INDEX));
    assertEquals(5, count(ALIAS));
    assertEquals(3, spacesOfThePlainGrouping());
  }

  @Test
  void anIndexWhoseCopyDoesNotHoldEveryDocumentIsLeftAsItIsAndTheOthersAreMigrated() throws Exception {
    String oldIndexId = indexSettings(OLD_INDEX).getString("uuid");
    // The engine copies one of the two documents of the older week
    doAnswer(invocation -> {
      String content = invocation.getArgument(1);
      if (content.contains("\"" + OLD_COPY + "\"")) {
        invocation.getRawArguments()[1] = new JSONObject(content).put("max_docs", 1).toString();
      }
      return invocation.callRealMethod();
    }).when(storage).sendPostRequest(startsWith("_reindex"), any());

    List<String> failedIndices = migrationService.migrate();

    assertEquals(List.of(OLD_INDEX), failedIndices);
    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(oldIndexId, indexSettings(OLD_INDEX).getString("uuid"), "the index that failed is the original one");
    assertEquals("long", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(2, count(OLD_INDEX));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals("keyword", fieldType(OLDEST_INDEX, "spaceId"));
    assertEquals(5, count(ALIAS));
  }

  @Test
  void aCloneThatDoesNotHoldEveryDocumentLeavesTheAliasToTheCopyAndTheNextRunClonesItAgain() throws Exception {
    // The second count of the older week is the one of its clone
    AtomicInteger counts = new AtomicInteger();
    doAnswer(invocation -> {
      ElasticsearchResponse response = (ElasticsearchResponse) invocation.callRealMethod();
      return counts.incrementAndGet() == 2 ? new ElasticsearchResponse("{\"count\":1}", 200) : response;
    }).when(storage).sendGetRequest(OLD_INDEX + "/_count");

    assertEquals(List.of(OLD_INDEX), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_COPY, NEWEST_INDEX), indices("/_alias/" + ALIAS), "the copy keeps the alias");
    assertEquals(404, send("GET", "/" + OLD_INDEX, null).statusCode(), "the clone that cannot be used is deleted");
    assertEquals(5, count(ALIAS));
    assertEquals(3, spacesOfThePlainGrouping());

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals(2, count(OLD_INDEX));
    assertEquals(5, count(ALIAS));
  }

  @Test
  void nothingIsMigratedWhileTheNewestIndexHoldsTheIdsAsNumbersAndTheOlderOnesAreOnceAnIndexOfTheTemplateIsTheNewest() throws Exception {
    // A platform at its first start on the version that types the ids as
    // keywords: the index of the current week was created by the former
    // template
    HttpResponse<String> deleted = send("DELETE", "/" + NEWEST_INDEX, null);
    assertEquals(200, deleted.statusCode(), deleted::body);
    createIndex(NEWEST_INDEX, "long", "long", true);
    index(NEWEST_INDEX, "4", event(4000, 1, 7, 15));
    index(NEWEST_INDEX, "5", event(5000, 3, 8, 15));
    refresh();
    String oldestIndexId = indexSettings(OLDEST_INDEX).getString("uuid");
    String oldIndexId = indexSettings(OLD_INDEX).getString("uuid");

    assertEquals(List.of(), migrationService.migrate());

    assertFalse(migrationService.isNewestIndexTypedAsTheTemplate(), "the migration is not complete");
    assertEquals(oldestIndexId, indexSettings(OLDEST_INDEX).getString("uuid"));
    assertEquals(oldIndexId, indexSettings(OLD_INDEX).getString("uuid"));
    assertEquals(3, spacesOfThePlainGrouping(), "every index holds the ids as numbers: the plain grouping reads them all");

    // The next week is created by the template
    HttpResponse<String> notWritten = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}}]}
        """.formatted(NEWEST_INDEX, ALIAS));
    assertEquals(200, notWritten.statusCode(), notWritten::body);
    createIndex(NEXT_INDEX, "keyword", "long", true);
    index(NEXT_INDEX, "6", event(6000, "4", "7", 15));
    refresh();

    assertEquals(List.of(), migrationService.migrate());

    assertTrue(migrationService.isNewestIndexTypedAsTheTemplate());
    refresh();
    for (String index : List.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX)) {
      assertEquals("keyword", fieldType(index, "spaceId"), index);
    }
    assertEquals(6, count(ALIAS));
    assertEquals(4, spacesOfThePlainGrouping());
  }

  @Test
  void aPlatformWithNoAnalyticsIndexHasNothingToMigrateAndItsMigrationIsComplete() throws Exception {
    cleanCluster();

    assertEquals(List.of(), migrationService.migrate());

    assertTrue(migrationService.isNewestIndexTypedAsTheTemplate());
    assertEquals(Set.of(), indices("/" + PREFIX + "_*"));
    assertEquals(Set.of(), indices("/" + COPIES));
  }

  @Test
  void anIndexThatCannotBeReadAgainAtItsTurnIsLeftAsItIsAndTheOthersAreMigrated() throws Exception {
    String oldestIndexId = indexSettings(OLDEST_INDEX).getString("uuid");
    // The first read of the names behind the alias, at the turn of the oldest
    // week, fails
    AtomicBoolean failed = new AtomicBoolean();
    doAnswer(invocation -> {
      if (failed.compareAndSet(false, true)) {
        throw new IllegalStateException("The engine does not answer");
      }
      return invocation.callRealMethod();
    }).when(storage).sendGetRequest("_alias/" + ALIAS);

    assertEquals(List.of(OLDEST_INDEX), migrationService.migrate());

    refresh();
    assertEquals(oldestIndexId, indexSettings(OLDEST_INDEX).getString("uuid"), "the index that failed is the original one");
    assertEquals("long", fieldType(OLDEST_INDEX, "spaceId"));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals(5, count(ALIAS));
  }

  @Test
  void aRunDoesNothingWhileAnotherOneHoldsTheLock() throws Exception {
    lock(System.currentTimeMillis());
    String oldestIndexId = indexSettings(OLDEST_INDEX).getString("uuid");
    String oldIndexId = indexSettings(OLD_INDEX).getString("uuid");

    assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());

    assertEquals(oldestIndexId, indexSettings(OLDEST_INDEX).getString("uuid"));
    assertEquals(oldIndexId, indexSettings(OLD_INDEX).getString("uuid"));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock of the other run is left");
  }

  @Test
  void aLockThatWasNotRenewedIsTakenOverAndReleasedAtTheEndOfTheRun() throws Exception {
    lock(System.currentTimeMillis() - AnalyticsIndexTypeMigrationService.LOCK_EXPIRY_MS - 1000);

    assertEquals(List.of(), migrationService.migrate());

    assertEquals("keyword", fieldType(OLDEST_INDEX, "spaceId"));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(404, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock is released");
    // And a run that follows takes it
    assertEquals(List.of(), migrationService.migrate());
  }

  @Test
  void aLockTakenOverByAnotherRunBetweenItsReadAndItsWriteIsLeftToThatRun() throws Exception {
    lock(System.currentTimeMillis() - AnalyticsIndexTypeMigrationService.LOCK_EXPIRY_MS - 1000);
    String oldIndexId = indexSettings(OLD_INDEX).getString("uuid");
    // Another run takes the expired lock over right after this one read it
    doAnswer(invocation -> {
      Object read = invocation.callRealMethod();
      HttpResponse<String> takenOver = send("PUT", "/" + LOCK + "/_doc/run?refresh=true", """
          {"timestamp":%s}
          """.formatted(System.currentTimeMillis()));
      assertEquals(200, takenOver.statusCode(), takenOver::body);
      return read;
    }).when(storage).sendGetRequest(LOCK + "/_doc/run", false);

    assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());

    assertEquals(oldIndexId, indexSettings(OLD_INDEX).getString("uuid"));
    assertEquals("long", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock of the other run is left");
  }

  @Test
  void aLockReleasedBetweenItsConflictAndItsReadIsTaken() throws Exception {
    lock(System.currentTimeMillis());
    // The other run ends and releases its lock right before this one reads it
    doAnswer(invocation -> {
      HttpResponse<String> released = send("DELETE", "/" + LOCK + "/_doc/run?refresh=true", null);
      assertEquals(200, released.statusCode(), released::body);
      return invocation.callRealMethod();
    }).when(storage).sendGetRequest(LOCK + "/_doc/run", false);

    assertEquals(List.of(), migrationService.migrate());

    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(404, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock is released");
  }

  @Test
  void aRunWhoseLockIsTakenOverStopsAtItsNextRenewalAndLeavesTheLockToTheOtherRun() throws Exception {
    String oldIndexId = indexSettings(OLD_INDEX).getString("uuid");
    // Another run takes the lock over while the oldest week is copied
    AtomicBoolean takenOver = new AtomicBoolean();
    doAnswer(invocation -> {
      if (takenOver.compareAndSet(false, true)) {
        HttpResponse<String> written = send("PUT", "/" + LOCK + "/_doc/run?refresh=true", """
            {"timestamp":%s}
            """.formatted(System.currentTimeMillis()));
        assertEquals(200, written.statusCode(), written::body);
      }
      return invocation.callRealMethod();
    }).when(storage).sendPostRequest(startsWith("_reindex"), any());

    assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());

    refresh();
    assertEquals("keyword", fieldType(OLDEST_INDEX, "spaceId"), "the week in progress was ended");
    assertEquals(oldIndexId, indexSettings(OLD_INDEX).getString("uuid"), "the next week was not started");
    assertEquals("long", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock of the other run is left");
    assertEquals(5, count(ALIAS));
  }

  @Test
  void aRunWhoseLockIsTakenOverBeforeItResumesACopyLeavesThatCopy() throws Exception {
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    index(OLD_COPY, "2", event(2000, 2, 7, 15));
    refresh();
    // Another run takes the lock over right after this one listed the copies:
    // the copy is that run's from then on
    doAnswer(invocation -> {
      Object listed = invocation.callRealMethod();
      HttpResponse<String> written = send("PUT", "/" + LOCK + "/_doc/run?refresh=true", """
          {"timestamp":%s}
          """.formatted(System.currentTimeMillis()));
      assertEquals(200, written.statusCode(), written::body);
      return listed;
    }).when(storage).sendGetRequest(COPIES + "?allow_no_indices=true&ignore_unavailable=true");

    assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());

    assertEquals(200, send("GET", "/" + OLD_COPY, null).statusCode(), "the copy of the other run is left");
    assertEquals("long", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock of the other run is left");
  }

  @Test
  void aRunWhoseLockIsTakenOverWhileItCancelsACopyLeavesThatCopy() throws Exception {
    for (int i = 10; i < 40; i++) {
      index(OLD_INDEX, String.valueOf(i), event(1000 + i, 2, 7, 15));
    }
    refresh();
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    HttpResponse<String> runningCopy = send("POST", "/_reindex?wait_for_completion=false&requests_per_second=1", """
        {"source":{"index":"%s","size":1},"dest":{"index":"%s"}}
        """.formatted(OLD_INDEX, OLD_COPY));
    assertEquals(200, runningCopy.statusCode(), runningCopy::body);
    // Another run takes the lock over while this one cancels the copy
    AtomicBoolean takenOver = new AtomicBoolean();
    doAnswer(invocation -> {
      if (takenOver.compareAndSet(false, true)) {
        HttpResponse<String> written = send("PUT", "/" + LOCK + "/_doc/run?refresh=true", """
            {"timestamp":%s}
            """.formatted(System.currentTimeMillis()));
        assertEquals(200, written.statusCode(), written::body);
      }
      return invocation.callRealMethod();
    }).when(storage).sendPostRequest(startsWith("_tasks/"), any());

    assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());

    assertEquals(200, send("GET", "/" + OLD_COPY, null).statusCode(), "the copy is not deleted");
    assertEquals("long", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock of the other run is left");
  }

  @Test
  void aRunWhoseLockIsTakenOverBeforeItDeletesAnUnfinishedCopyLeavesThatCopy() throws Exception {
    // The copy of the older week does not hold every document, and another
    // run takes the lock over before this one deletes it
    doAnswer(invocation -> {
      String content = invocation.getArgument(1);
      if (content.contains("\"" + OLD_COPY + "\"")) {
        invocation.getRawArguments()[1] = new JSONObject(content).put("max_docs", 1).toString();
        HttpResponse<String> written = send("PUT", "/" + LOCK + "/_doc/run?refresh=true", """
            {"timestamp":%s}
            """.formatted(System.currentTimeMillis()));
        assertEquals(200, written.statusCode(), written::body);
      }
      return invocation.callRealMethod();
    }).when(storage).sendPostRequest(startsWith("_reindex"), any());

    assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());

    assertEquals(200, send("GET", "/" + OLD_COPY, null).statusCode(), "the copy is not deleted");
    assertEquals("long", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock of the other run is left");
  }

  @Test
  void aSecondRunInTheSameServerIsRefusedAndLeavesTheFirstOneItsLock() throws Exception {
    AtomicBoolean second = new AtomicBoolean();
    doAnswer(invocation -> {
      if (second.compareAndSet(false, true)) {
        int requests = mockingDetails(storage).getInvocations().size();
        assertThrows(AnalyticsMigrationRunningException.class, () -> migrationService.migrate());
        assertEquals(requests,
                     mockingDetails(storage).getInvocations().size(),
                     "the second run is refused before it sends a request, the read of the lock included");
        assertEquals(200, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the first run keeps its lock");
      }
      return invocation.callRealMethod();
    }).when(storage).sendPostRequest(startsWith("_reindex"), any());

    assertEquals(List.of(), migrationService.migrate());

    assertTrue(second.get());
    assertEquals("keyword", fieldType(OLDEST_INDEX, "spaceId"));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(404, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock is released");
    // And a run that follows is not refused
    assertEquals(List.of(), migrationService.migrate());
  }

  @Test
  void aCopyThatTookTheAliasSinceTheCopiesWereListedIsNotDeleted() throws Exception {
    // A complete copy without the alias, as another run leaves it right
    // before its first exchange
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"},"rank":{"type":"long"},
           "timestamp":{"type":"date","format":"epoch_millis"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    index(OLD_COPY, "2", event(2000, 2, 7, 15));
    index(OLD_COPY, "3", event(3000, 1, 8, 15));
    refresh();
    // That exchange lands right after this run listed the copies
    doAnswer(invocation -> {
      Object listed = invocation.callRealMethod();
      HttpResponse<String> swap = send("POST", "/_aliases", """
          {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}},{"remove_index":{"index":"%s"}}]}
          """.formatted(OLD_COPY, ALIAS, OLD_INDEX));
      assertEquals(200, swap.statusCode(), swap::body);
      return listed;
    }).when(storage).sendGetRequest(COPIES + "?allow_no_indices=true&ignore_unavailable=true");

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(2, count(OLD_INDEX), "the week the copy held is kept");
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(5, count(ALIAS));
  }

  @Test
  void aFieldTheTemplateDoesNotNameKeepsItsTypeWhateverTheNewestIndexGivesIt() throws Exception {
    // The newest week types the rank as a keyword: its first value there was
    // written as a text
    HttpResponse<String> deleted = send("DELETE", "/" + NEWEST_INDEX, null);
    assertEquals(200, deleted.statusCode(), deleted::body);
    createIndex(NEWEST_INDEX, "keyword", "keyword", true);
    index(NEWEST_INDEX, "4", event(4000, "1", "7", "15"));
    index(NEWEST_INDEX, "5", event(5000, "3", "8", "15"));
    refresh();

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    for (String index : List.of(OLDEST_INDEX, OLD_INDEX)) {
      assertEquals("keyword", fieldType(index, "spaceId"), index);
      assertEquals("long", fieldType(index, "rank"), index);
    }
    // The engine still answers a maximum of the rank on the migrated weeks
    HttpResponse<String> maximum = send("POST", "/" + OLDEST_INDEX + "," + OLD_INDEX + "/_search", """
        {"size":0,"aggs":{"rank":{"max":{"field":"rank"}}}}
        """);
    assertEquals(200, maximum.statusCode(), maximum::body);
    assertEquals(15, new JSONObject(maximum.body()).getJSONObject("aggregations").getJSONObject("rank").getInt("value"));
  }

  @Test
  void theCopyKeepsTheLimitOnTheNumberOfFieldsOfItsOriginal() throws Exception {
    HttpResponse<String> raised = send("PUT", "/" + OLD_INDEX + "/_settings", """
        {"index.mapping.total_fields.limit":2000}
        """);
    assertEquals(200, raised.statusCode(), raised::body);

    assertEquals(List.of(), migrationService.migrate());

    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals("2000",
                 indexSettings(OLD_INDEX).getJSONObject("mapping").getJSONObject("total_fields").getString("limit"));
    assertFalse(indexSettings(OLDEST_INDEX).has("mapping"));
  }

  @Test
  void anIndexTheEngineDoesNotAnswerForAtItsTurnIsLeftAsItIsAndTheOthersAreMigrated() throws Exception {
    String oldestIndexId = indexSettings(OLDEST_INDEX).getString("uuid");
    // The connection breaks at the first read of the names behind the alias:
    // the storage throws the client's exception as it is
    AtomicBoolean failed = new AtomicBoolean();
    doAnswer(invocation -> {
      if (failed.compareAndSet(false, true)) {
        throw new SocketTimeoutException("Read timed out");
      }
      return invocation.callRealMethod();
    }).when(storage).sendGetRequest("_alias/" + ALIAS);

    assertEquals(List.of(OLDEST_INDEX), migrationService.migrate());

    refresh();
    assertEquals(oldestIndexId, indexSettings(OLDEST_INDEX).getString("uuid"));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals(404, send("GET", "/" + LOCK + "/_doc/run", null).statusCode(), "the lock is released");
    assertEquals(5, count(ALIAS));
  }

  @Test
  void aCloneTheEngineDoesNotAnswerForIsDeletedAndTheCopyKeepsTheAlias() throws Exception {
    // The second count of the older week is the one of its clone
    AtomicInteger counts = new AtomicInteger();
    doAnswer(invocation -> {
      if (counts.incrementAndGet() == 2) {
        throw new SocketTimeoutException("Read timed out");
      }
      return invocation.callRealMethod();
    }).when(storage).sendGetRequest(OLD_INDEX + "/_count");

    assertEquals(List.of(OLD_INDEX), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_COPY, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(404, send("GET", "/" + OLD_INDEX, null).statusCode(), "the clone that cannot be used is deleted");
    assertEquals(5, count(ALIAS));
  }

  @Test
  void aCloneIsNotDeletedOnceItsCopyDoesNotCarryTheAliasAnyMore() throws Exception {
    // Another run ends the migration of the older week while this one counts
    // its clone: the clone has the alias and the copy is deleted
    AtomicInteger counts = new AtomicInteger();
    doAnswer(invocation -> {
      if (counts.incrementAndGet() == 2) {
        HttpResponse<String> swap = send("POST", "/_aliases", """
            {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}},{"remove_index":{"index":"%s"}}]}
            """.formatted(OLD_INDEX, ALIAS, OLD_COPY));
        assertEquals(200, swap.statusCode(), swap::body);
        throw new IllegalStateException("The count of the copy fails: it is deleted");
      }
      return invocation.callRealMethod();
    }).when(storage).sendGetRequest(OLD_INDEX + "/_count");

    assertEquals(List.of(OLD_INDEX), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(2, count(OLD_INDEX), "the index that holds the week is not deleted");
    assertEquals(5, count(ALIAS));
  }

  @Test
  void anIndexOfAWeeklyNameIsNotDeletedOnceItsCopyDoesNotCarryTheAliasAnyMore() throws Exception {
    // A copy has the alias and an index has the name of the original: a clone
    // to make again, unless another run ends the migration meanwhile
    HttpResponse<String> copy = send("PUT", "/" + OLD_COPY, """
        {"settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{"spaceId":{"type":"keyword"},"userId":{"type":"keyword"},"rank":{"type":"long"},
           "timestamp":{"type":"date","format":"epoch_millis"}}}}
        """);
    assertEquals(200, copy.statusCode(), copy::body);
    index(OLD_COPY, "2", event(2000, 2, 7, 15));
    index(OLD_COPY, "3", event(3000, 1, 8, 15));
    HttpResponse<String> swap = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}},{"remove_index":{"index":"%s"}}]}
        """.formatted(OLD_COPY, ALIAS, OLD_INDEX));
    assertEquals(200, swap.statusCode(), swap::body);
    createIndex(OLD_INDEX, "keyword", "long", false);
    HttpResponse<String> noAlias = send("POST", "/_aliases", """
        {"actions":[{"remove":{"index":"%s","alias":"%s"}}]}
        """.formatted(OLD_INDEX, ALIAS));
    assertEquals(200, noAlias.statusCode(), noAlias::body);
    index(OLD_INDEX, "2", event(2000, 2, 7, 15));
    index(OLD_INDEX, "3", event(3000, 1, 8, 15));
    refresh();
    String indexId = indexSettings(OLD_INDEX).getString("uuid");
    // The other run's last exchange lands after the resume read the copy
    // with the alias, before this run reads it again to delete the index
    AtomicInteger reads = new AtomicInteger();
    doAnswer(invocation -> {
      if (reads.incrementAndGet() == 2) {
        HttpResponse<String> lastExchange = send("POST", "/_aliases", """
            {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}},{"remove_index":{"index":"%s"}}]}
            """.formatted(OLD_INDEX, ALIAS, OLD_COPY));
        assertEquals(200, lastExchange.statusCode(), lastExchange::body);
      }
      return invocation.callRealMethod();
    }).when(storage).sendGetRequest(OLD_COPY + "?allow_no_indices=true&ignore_unavailable=true");

    assertEquals(List.of(OLD_INDEX), migrationService.migrate());

    refresh();
    assertEquals(indexId, indexSettings(OLD_INDEX).getString("uuid"), "the index that holds the week is not deleted");
    assertEquals(2, count(OLD_INDEX));
    assertEquals(Set.of(OLDEST_INDEX, OLD_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
  }

  @Test
  void anIndexDeletedSinceTheListWasMadeIsSkipped() throws Exception {
    // The retention deletes the older week while the oldest one is copied
    AtomicBoolean deleted = new AtomicBoolean();
    doAnswer(invocation -> {
      if (deleted.compareAndSet(false, true)) {
        assertEquals(200, send("DELETE", "/" + OLD_INDEX, null).statusCode());
      }
      return invocation.callRealMethod();
    }).when(storage).sendPostRequest(startsWith("_reindex"), any());

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertEquals(Set.of(OLDEST_INDEX, NEWEST_INDEX), indices("/_alias/" + ALIAS));
    assertEquals(Set.of(), indices("/" + COPIES));
    assertEquals("keyword", fieldType(OLDEST_INDEX, "spaceId"));
    assertEquals(3, count(ALIAS));
  }

  @Test
  void aFieldTheNewestIndexDoesNotTypeAsAKeywordIsLeftAsItIs() throws Exception {
    // The older week holds, as a keyword, a rank no number can hold
    HttpResponse<String> deleted = send("DELETE", "/" + OLD_INDEX, null);
    assertEquals(200, deleted.statusCode(), deleted::body);
    createIndex(OLD_INDEX, "long", "keyword", false);
    // The template gave it the alias as a write index too
    HttpResponse<String> notWritten = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}}]}
        """.formatted(OLD_INDEX, ALIAS));
    assertEquals(200, notWritten.statusCode(), notWritten::body);
    index(OLD_INDEX, "2", event(2000, 2, 7, "first"));
    index(OLD_INDEX, "3", event(3000, 1, 8, "second"));
    refresh();

    assertEquals(List.of(), migrationService.migrate());

    refresh();
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
    assertEquals("keyword", fieldType(OLD_INDEX, "rank"), "a keyword the newest index types as a number is not converted");
    assertEquals(2, count(OLD_INDEX));
    assertEquals(5, count(ALIAS));
    assertEquals(3, spacesOfThePlainGrouping());
  }

  @Test
  void anIndexNamedAfterThePrefixThatIsNoWeekIsNeitherTheReferenceNorMigrated() throws Exception {
    // Its name sorts after every week, and it holds the ids as numbers
    createIndex(OTHER_INDEX, "long", "long", false);
    HttpResponse<String> notWritten = send("POST", "/_aliases", """
        {"actions":[{"add":{"index":"%s","alias":"%s","is_write_index":false}}]}
        """.formatted(OTHER_INDEX, ALIAS));
    assertEquals(200, notWritten.statusCode(), notWritten::body);
    String otherIndexId = indexSettings(OTHER_INDEX).getString("uuid");

    assertEquals(List.of(), migrationService.migrate());

    assertEquals(otherIndexId, indexSettings(OTHER_INDEX).getString("uuid"));
    assertEquals("long", fieldType(OTHER_INDEX, "spaceId"));
    assertEquals("keyword", fieldType(OLDEST_INDEX, "spaceId"));
    assertEquals("keyword", fieldType(OLD_INDEX, "spaceId"));
  }

  private int usersOfThePlainDistinctCount() throws Exception {
    HttpResponse<String> response = send("POST", "/" + ALIAS + "/_search", """
        {"size":0,"aggs":{"users":{"cardinality":{"field":"userId"}}}}
        """);
    assertEquals(200, response.statusCode(), response::body);
    JSONObject answer = new JSONObject(response.body());
    assertEquals(0, answer.getJSONObject("_shards").getInt("failed"), response::body);
    return answer.getJSONObject("aggregations").getJSONObject("users").getInt("value");
  }

  private int runningCopies() throws Exception {
    HttpResponse<String> response = send("GET", "/_tasks?actions=indices:data/write/reindex&detailed=true", null);
    assertEquals(200, response.statusCode(), response::body);
    int copies = 0;
    JSONObject nodes = new JSONObject(response.body()).getJSONObject("nodes");
    for (String node : nodes.keySet()) {
      JSONObject tasks = nodes.getJSONObject(node).getJSONObject("tasks");
      for (String task : tasks.keySet()) {
        if (tasks.getJSONObject(task).getString("description").contains(OLD_COPY)) {
          copies++;
        }
      }
    }
    return copies;
  }

  private int spacesOfThePlainGrouping() throws Exception {
    HttpResponse<String> response = send("POST", "/" + ALIAS + "/_search", """
        {"size":0,"aggs":{"spaces":{"terms":{"field":"spaceId"}}}}
        """);
    assertEquals(200, response.statusCode(), response::body);
    JSONObject answer = new JSONObject(response.body());
    assertEquals(0, answer.getJSONObject("_shards").getInt("failed"), response::body);
    return answer.getJSONObject("aggregations").getJSONObject("spaces").getJSONArray("buckets").length();
  }

  private Set<String> indices(String path) throws Exception {
    HttpResponse<String> response = send("GET", path + "?allow_no_indices=true", null);
    assertEquals(200, response.statusCode(), response::body);
    return new TreeSet<>(new JSONObject(response.body()).keySet());
  }

  private String fieldType(String index, String field) throws Exception {
    HttpResponse<String> response = send("GET", "/" + index + "/_mapping", null);
    assertEquals(200, response.statusCode(), response::body);
    return new JSONObject(response.body()).getJSONObject(index)
                                          .getJSONObject("mappings")
                                          .getJSONObject("properties")
                                          .getJSONObject(field)
                                          .getString("type");
  }

  private JSONObject indexSettings(String index) throws Exception {
    HttpResponse<String> response = send("GET", "/" + index + "/_settings", null);
    assertEquals(200, response.statusCode(), response::body);
    return new JSONObject(response.body()).getJSONObject(index).getJSONObject("settings").getJSONObject("index");
  }

  private long count(String index) throws Exception {
    HttpResponse<String> response = send("GET", "/" + index + "/_count", null);
    assertEquals(200, response.statusCode(), response::body);
    return new JSONObject(response.body()).getLong("count");
  }

  private void lock(long timestamp) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + LOCK + "/_create/run?refresh=true", """
        {"timestamp":%s}
        """.formatted(timestamp));
    assertEquals(201, response.statusCode(), response::body);
  }

  private void refresh() throws Exception {
    HttpResponse<String> response = send("POST", "/_refresh", null);
    assertEquals(200, response.statusCode(), response::body);
  }

  private static JSONObject event(long timestamp, Object spaceId, Object userId, Object rank) {
    JSONObject event = new JSONObject();
    event.put("timestamp", timestamp);
    event.put("userId", userId);
    event.put("spaceId", spaceId);
    event.put("rank", rank);
    event.put("operation", "createActivity");
    return event;
  }

  private void index(String index, String id, JSONObject event) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + index + "/_doc/" + id, event.toString());
    assertTrue(response.statusCode() == 200 || response.statusCode() == 201, response::body);
  }

  private void createIndex(String index, String idType, String rankType, boolean writeIndex) throws Exception {
    HttpResponse<String> response = send("PUT", "/" + index, """
        {"aliases":{"%s":{"is_write_index":%s}},
         "settings":{"number_of_shards":1,"number_of_replicas":0},
         "mappings":{"properties":{
           "timestamp":{"type":"date","format":"epoch_millis"},
           "userId":{"type":"%s"}, "spaceId":{"type":"%s"}, "rank":{"type":"%s"},
           "operation":{"type":"keyword"}}}}
        """.formatted(ALIAS, writeIndex, idType, idType, rankType));
    assertEquals(200, response.statusCode(), response::body);
  }

  private void cleanCluster() throws Exception {
    send("DELETE", "/_index_template/" + TEMPLATE, null);
    send("DELETE", "/" + LOCK, null);
    for (String pattern : List.of(PREFIX + "_*", COPIES)) {
      for (String index : indices("/" + pattern)) {
        HttpResponse<String> deleted = send("DELETE", "/" + index, null);
        assertEquals(200, deleted.statusCode(), deleted::body);
      }
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
