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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;

import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticFieldMapping;

/**
 * Unit tests for the merge of the weekly indices' field mappings in
 * {@link ElasticsearchAnalyticsService}.
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
