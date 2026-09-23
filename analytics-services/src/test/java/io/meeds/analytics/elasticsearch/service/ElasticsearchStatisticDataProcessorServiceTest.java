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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.meeds.analytics.elasticsearch.model.ElasticsearchMappingConflictException;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.model.StatisticFieldMapping;

@ExtendWith(MockitoExtension.class)
class ElasticsearchStatisticDataProcessorServiceTest {

  private static final String                        REASON         =
                                                            "failed to parse field [profileProperties.country] of type [long]";

  @Mock
  private ElasticsearchAnalyticsStorage              storage;

  @Mock
  private ElasticsearchAnalyticsService              analyticsService;

  @InjectMocks
  private ElasticsearchStatisticDataProcessorService processor;

  private final Set<StatisticFieldMapping>           cachedMappings = Set.of(new StatisticFieldMapping("profileProperties.country",
                                                                                                          "keyword",
                                                                                                          false));

  // A mapping's equality is its name (Lombok @Exclude on the other fields),
  // so the refreshed set must differ by a field, not only by a type, for
  // Mockito to tell the two bulks apart.
  private final Set<StatisticFieldMapping>           freshMappings  = Set.of(new StatisticFieldMapping("profileProperties.country",
                                                                                                          "long",
                                                                                                          false),
                                                                                new StatisticFieldMapping("profileProperties.country_alt",
                                                                                                          "keyword",
                                                                                                          false));

  private final StatisticDataQueueEntry              refused        = entry("login");

  private final StatisticDataQueueEntry              created        = entry("saveUser");

  private final List<StatisticDataQueueEntry>        entries        = List.of(refused, created);

  @BeforeEach
  void setUp() {
    when(analyticsService.retrieveMapping(false)).thenReturn(cachedMappings);
  }

  @Test
  void aTypeConflictRefreshesTheMappingAndRetriesTheRefusedDocumentsOnly() {
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    doThrow(conflict(Set.of(String.valueOf(refused.getId())))).when(storage)
                                                                .sendCreateBulkDocumentsRequest(anyList(), eq(cachedMappings));

    processor.process(entries);

    verify(analyticsService).retrieveMapping(true);
    verify(storage).sendCreateBulkDocumentsRequest(entries, cachedMappings);
    verify(storage).sendCreateBulkDocumentsRequest(List.of(refused), freshMappings);
  }

  /**
   * Pins the exception's empty-id contract: no id carried means the whole
   * batch is retried. Reachable today only through a refused item without
   * {@code _id} (the storage then carries no id at all, see
   * {@code ElasticsearchAnalyticsStorageTest.aRefusedItemWithoutIdOrReasonIsStillClassifiedAsAConflict});
   * an unreadable bulk body is a generic error, never a conflict.
   */
  @Test
  void aTypeConflictCarryingNoIdRetriesTheWholeBatch() {
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    doThrow(new ElasticsearchMappingConflictException("refused item without _id")).when(storage)
                                                                      .sendCreateBulkDocumentsRequest(anyList(),
                                                                                                      eq(cachedMappings));

    processor.process(entries);

    verify(storage).sendCreateBulkDocumentsRequest(entries, freshMappings);
  }

  /**
   * Elasticsearch reports every parsing refusal with one error type, so a
   * refusal a mapping read cannot fix reaches this path too: a field-limit
   * refusal, verified on 9.5.3 to answer {@code document_parsing_exception}
   * with the reason pinned below. Each read is a full alias mapping request,
   * a merge and a settings write, and the dispatcher re-sends every refused
   * document on its own, so an unthrottled path would pay one per document
   * and per attempt, every ten seconds. The first refusal still reads the
   * mapping — a genuinely stale view must always be re-read.
   */
  @Test
  void repeatedRefusalsReadTheMappingOncePerWindow() {
    ReflectionTestUtils.setField(processor, "mappingRefreshMinIntervalSeconds", 300L);
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    doThrow(fieldLimitRefusal()).when(storage).sendCreateBulkDocumentsRequest(anyList(), eq(cachedMappings));
    // A field limit is not a stale view: the retry is refused too.
    doThrow(fieldLimitRefusal()).when(storage).sendCreateBulkDocumentsRequest(anyList(), eq(freshMappings));

    for (int attempt = 0; attempt < 3; attempt++) {
      assertThrows(ElasticsearchMappingConflictException.class, () -> processor.process(entries));
    }

    verify(analyticsService, times(1)).retrieveMapping(true);
    verify(analyticsService, times(5)).retrieveMapping(false);
    verify(storage, times(6)).sendCreateBulkDocumentsRequest(anyList(), anySet());
  }

  @Test
  void refusedIdsMatchingNoEntryRetryTheWholeBatch() {
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    doThrow(conflict(Set.of("not-an-entry-id"))).when(storage).sendCreateBulkDocumentsRequest(anyList(), eq(cachedMappings));

    processor.process(entries);

    verify(storage).sendCreateBulkDocumentsRequest(entries, freshMappings);
  }

  @Test
  void aSecondTypeConflictAfterTheRefreshPropagates() {
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    ElasticsearchMappingConflictException second = conflict(Set.of(String.valueOf(refused.getId())));
    doThrow(conflict(Set.of(String.valueOf(refused.getId())))).when(storage)
                                                                .sendCreateBulkDocumentsRequest(anyList(), eq(cachedMappings));
    doThrow(second).when(storage).sendCreateBulkDocumentsRequest(anyList(), eq(freshMappings));

    ElasticsearchMappingConflictException thrown = assertThrows(ElasticsearchMappingConflictException.class,
                                                                () -> processor.process(entries));

    assertSame(second, thrown, "no endless retry: the second refusal is the caller's to handle");
    verify(storage, times(2)).sendCreateBulkDocumentsRequest(anyList(), anySet());
  }

  @Test
  void anotherElasticsearchErrorIsNotRetried() {
    IllegalStateException error = new IllegalStateException("Error message returned from ES: cluster_block_exception");
    doThrow(error).when(storage).sendCreateBulkDocumentsRequest(anyList(), eq(cachedMappings));

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> processor.process(entries));

    assertSame(error, thrown);
    verify(analyticsService, never()).retrieveMapping(true);
    verify(storage, times(1)).sendCreateBulkDocumentsRequest(anyList(), anySet());
  }

  private static ElasticsearchMappingConflictException conflict(Set<String> refusedIds) {
    return new ElasticsearchMappingConflictException("refused", refusedIds, List.of(REASON));
  }

  private ElasticsearchMappingConflictException fieldLimitRefusal() {
    return new ElasticsearchMappingConflictException("refused",
                                                     Set.of(String.valueOf(refused.getId())),
                                                     List.of("failed to parse: Limit of total fields [1000] has been exceeded while adding new fields [1]"));
  }

  private static StatisticDataQueueEntry entry(String operation) {
    StatisticData data = new StatisticData();
    data.setModule("portal");
    data.setSubModule("test");
    data.setOperation(operation);
    data.setTimestamp(System.currentTimeMillis());
    data.setUserId(1);
    return new StatisticDataQueueEntry(data);
  }

}
