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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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

import io.meeds.analytics.elasticsearch.model.ElasticsearchMappingConflictException;
import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticDataQueueEntry;
import io.meeds.analytics.model.StatisticFieldMapping;

@ExtendWith(MockitoExtension.class)
class ElasticsearchStatisticDataProcessorServiceTest {

  @Mock
  private ElasticsearchAnalyticsStorage           storage;

  @Mock
  private ElasticsearchAnalyticsService           analyticsService;

  @InjectMocks
  private ElasticsearchStatisticDataProcessorService processor;

  private final Set<StatisticFieldMapping>         cachedMappings = Set.of(new StatisticFieldMapping("profileProperties.country",
                                                                                                        "keyword",
                                                                                                        false));

  // A mapping's equality is its name (Lombok @Exclude on the other fields),
  // so the refreshed set must differ by a field, not only by a type, for
  // Mockito to tell the two bulks apart.
  private final Set<StatisticFieldMapping>         freshMappings  = Set.of(new StatisticFieldMapping("profileProperties.country",
                                                                                                        "long",
                                                                                                        false),
                                                                              new StatisticFieldMapping("profileProperties.country_alt",
                                                                                                        "keyword",
                                                                                                        false));

  private final List<StatisticDataQueueEntry>      entries        = List.of(new StatisticDataQueueEntry(new StatisticData()));

  @BeforeEach
  void setUp() {
    when(analyticsService.retrieveMapping(false)).thenReturn(cachedMappings);
  }

  @Test
  void aTypeConflictRefreshesTheMappingAndRetriesTheBulkOnce() {
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    doThrow(new ElasticsearchMappingConflictException("failed to parse field [profileProperties.country] of type [long]"))
                                                                                                                             .doNothing()
                                                                                                                             .when(storage)
                                                                                                                             .sendCreateBulkDocumentsRequest(anyList(),
                                                                                                                                                             eq(cachedMappings));
    doNothing().when(storage).sendCreateBulkDocumentsRequest(anyList(), eq(freshMappings));

    processor.process(entries);

    verify(analyticsService).retrieveMapping(true);
    verify(storage).sendCreateBulkDocumentsRequest(entries, cachedMappings);
    verify(storage).sendCreateBulkDocumentsRequest(entries, freshMappings);
  }

  @Test
  void aSecondTypeConflictAfterTheRefreshPropagates() {
    when(analyticsService.retrieveMapping(true)).thenReturn(freshMappings);
    ElasticsearchMappingConflictException second = new ElasticsearchMappingConflictException("still refused");
    doThrow(new ElasticsearchMappingConflictException("refused")).when(storage)
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

  private static Set<StatisticFieldMapping> anySet() {
    return org.mockito.ArgumentMatchers.anySet();
  }

}
