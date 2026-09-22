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
package io.meeds.analytics.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.meeds.analytics.model.StatisticFieldMapping;

class AnalyticsUtilsTest {

  /**
   * EXO-90504: the two indices hold the same sub-fields under
   * {@code profileProperties}, and the newest one changed the type of one of
   * them. The merge used to descend into an object only when the newer index
   * brought a new sub-field, so this exact shape kept the oldest type.
   */
  @Test
  void getJsonNodeMergesNestedPropertiesWithIdenticalKeysTakingTheNewestType() {
    JSONObject mappings = new JSONObject("""
        {"analytics_2026-09-17":{"mappings":{"properties":{
            "profileProperties":{"properties":{"country":{"type":"long"},"city":{"type":"keyword"}}}}}},
         "analytics_2026-09-10":{"mappings":{"properties":{
            "profileProperties":{"properties":{"country":{"type":"keyword"},"city":{"type":"keyword"}}}}}}}
        """);

    JsonNode merged = AnalyticsUtils.getJsonNode(AnalyticsUtils.sortByAnalyticsDate(mappings), 0, null, "mappings", "properties");

    JsonNode properties = merged.get("profileProperties").get("properties");
    assertEquals("long", properties.get("country").get("type").asText(), "the newest index's type must win");
    assertEquals("keyword", properties.get("city").get("type").asText());
  }

  @Test
  void sortByAnalyticsDateRecognisesTheConfiguredIndexPrefix() {
    JSONObject mappings = new JSONObject("""
        {"stats_2026-09-17":{"a":1},"stats_2026-09-03":{"a":2},"analytics_2026-09-10":{"a":3},"stats_other":{"a":4}}
        """);

    ObjectNode sorted = AnalyticsUtils.sortByAnalyticsDate(mappings, "stats");

    assertEquals(2, sorted.size(), "only <prefix>_yyyy-MM-dd indices are kept");
    assertEquals("stats_2026-09-03", sorted.fieldNames().next(), "oldest first");
    assertFalse(sorted.has("analytics_2026-09-10"));
    assertTrue(AnalyticsUtils.sortByAnalyticsDate(mappings).has("analytics_2026-09-10"), "the default keeps the product prefix");
    assertTrue(AnalyticsUtils.sortByAnalyticsDate(mappings, null).has("analytics_2026-09-10"), "a null prefix means the default");
  }

  @Test
  void convertFieldNameRedirectsToTheHighestAlternativeIncludingTheFourth() {
    Set<StatisticFieldMapping> mappings = mappings(mapping("field", "long", false, false),
                                                   mapping("field_alt", "keyword", false, false),
                                                   mapping("field_alt4", "keyword", false, false));

    assertEquals("field_alt4", convert("field", mappings, true));
    assertEquals("field_alt4", convert("field.keyword", mappings, false));
  }

  @Test
  void convertFieldNameSkipsAConflictedAlternativeWithoutKeywordSubField() {
    Set<StatisticFieldMapping> mappings = mappings(mapping("field", "long", false, false),
                                                   mapping("field_alt", "keyword", false, false),
                                                   mapping("field_alt2", "keyword", false, true));

    assertEquals("field_alt", convert("field", mappings, true));
    assertEquals("field_alt", convert("field", mappings, false));
  }

  @Test
  void convertFieldNameAggregatesAConflictedTextAlternativeThroughItsKeywordSubField() {
    Set<StatisticFieldMapping> mappings = mappings(mapping("field", "long", false, false),
                                                   mapping("field_alt", "keyword", false, false),
                                                   mapping("field_alt2", "text", true, true));

    assertEquals("field_alt2.keyword", convert("field", mappings, true));
    assertEquals("field_alt", convert("field", mappings, false));
  }

  @Test
  void convertFieldNameNeverSkipsTheBaseField() {
    Set<StatisticFieldMapping> conflictedKeyword = mappings(mapping("field", "keyword", false, true));
    assertEquals("field", convert("field", conflictedKeyword, true));

    Set<StatisticFieldMapping> conflictedText = mappings(mapping("field", "text", true, true));
    assertEquals("field.keyword", convert("field", conflictedText, true));
    assertEquals("field", convert("field", conflictedText, false));
  }

  @Test
  void convertFieldNameLeavesAnUnmappedFieldUntouched() {
    Set<StatisticFieldMapping> mappings = mappings(mapping("other", "keyword", false, false));

    assertNull(convert("field_alt3", mappings, true));
  }

  private static String convert(String fieldName, Set<StatisticFieldMapping> mappings, boolean isAggregation) {
    AtomicReference<String> result = new AtomicReference<>();
    AnalyticsUtils.convertFieldName(() -> fieldName, result::set, mappings, isAggregation);
    return result.get();
  }

  private static Set<StatisticFieldMapping> mappings(StatisticFieldMapping... fieldMappings) {
    Set<StatisticFieldMapping> mappings = new HashSet<>();
    for (StatisticFieldMapping mapping : fieldMappings) {
      mappings.add(mapping);
    }
    return mappings;
  }

  private static StatisticFieldMapping mapping(String name, String type, boolean hasKeywordSubField, boolean typeConflict) {
    StatisticFieldMapping mapping = new StatisticFieldMapping(name, type, hasKeywordSubField);
    mapping.setTypeConflict(typeConflict);
    return mapping;
  }
}
