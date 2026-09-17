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
package io.meeds.analytics.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;

import io.meeds.analytics.elasticsearch.storage.ElasticsearchAnalyticsStorage;
import io.meeds.analytics.model.StatisticFieldMapping;

@ExtendWith(MockitoExtension.class)
class ElasticsearchAnalyticsServiceTest {

  private static final String           OLDEST_INDEX  = "analytics_2026-07-30";

  private static final String           MIDDLE_INDEX  = "analytics_2026-08-06";

  private static final String           NEWEST_INDEX  = "analytics_2026-08-13";

  @Mock
  private ElasticsearchAnalyticsStorage storage;

  @Mock
  private SettingService                settingService;

  @InjectMocks
  private ElasticsearchAnalyticsService service;

  @Test
  void retrieveMappingMergesAllIndicesPreferringTheExplicitTypeOverADynamicText() {
    when(storage.retrieveAllAnalyticsIndexesMapping()).thenReturn(indicesMapping(
        // listed out of date order on purpose: the merge sorts by index date
        MIDDLE_INDEX, properties(text("contentId"), longField("contentUpdatedDate_alt2"), keyword("module")),
        NEWEST_INDEX, properties(text("contentId"), longField("contentUpdatedDate_alt2"), keyword("module"), text("title")),
        OLDEST_INDEX, properties(keyword("contentId"), keyword("contentUpdatedDate_alt2"), keyword("module"))));

    Map<String, StatisticFieldMapping> mappings = byName(service.retrieveMapping(true));

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
    verify(settingService).set(any(), any(), any(), any(SettingValue.class));
  }

  @Test
  void retrieveMappingFlagsANewestTextFieldOnlyWhenAggregatableTypesDisagree() {
    when(storage.retrieveAllAnalyticsIndexesMapping()).thenReturn(indicesMapping(
        OLDEST_INDEX, properties(keyword("field")),
        MIDDLE_INDEX, properties(longField("field")),
        NEWEST_INDEX, properties(text("field"))));

    StatisticFieldMapping field = byName(service.retrieveMapping(true)).get("field");

    assertEquals("text", field.getType(), "no single explicit type to restore: the most recent index wins");
    assertTrue(field.isHasKeywordSubField());
    assertTrue(field.isTypeConflict());
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
      json.append("\"").append(indexNameAndProperties[i]).append("\":{\"mappings\":{\"properties\":{")
          .append(indexNameAndProperties[i + 1]).append("}}}");
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
