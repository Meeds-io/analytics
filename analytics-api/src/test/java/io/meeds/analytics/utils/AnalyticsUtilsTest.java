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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.meeds.analytics.model.StatisticFieldMapping;

class AnalyticsUtilsTest {

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
