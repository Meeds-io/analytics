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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.exoplatform.social.core.space.model.Space;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticFieldMapping;

class AnalyticsUtilsTest {

  /**
   * The space attributes an event carries are exactly the table's names, each
   * written with the writer of its type: a count and a time as longs, the
   * sovereign flag as a boolean, the ids and enumerations as keywords. A loop
   * writing every value as a string would map the counts as keywords and break
   * every MAX or SUM on them.
   */
  @Test
  void addSpaceStatisticsWritesEveryTableAttributeWithItsType() {
    Space space = new Space();
    space.setId("42");
    space.setParentSpaceId(7L);
    space.setTemplateId(3L);
    space.setCategoryIds(List.of(5L, 6L));
    space.setVisibility("private");
    space.setRegistration("validation");
    space.setCreatedTime(1700000000000L);
    space.setMembers(new String[] { "a", "b", "b" });
    space.setManagers(new String[] { "a" });
    space.setRedactors(new String[] {});
    space.setInvitedUsers(new String[] { "c" });
    space.setPendingUsers(new String[] { "d", "e" });
    space.setSovereign(true);
    StatisticData data = new StatisticData();
    data.setOperation("pageDisplay");

    AnalyticsUtils.addSpaceStatistics(data, space);

    assertEquals(42L, data.getSpaceId());
    Map<String, Object> parameters = data.getParameters();
    Set<String> written = new HashSet<>(parameters.keySet());
    written.addAll(data.getListParameters().keySet());
    assertEquals(AnalyticsUtils.SPACE_ATTRIBUTE_NAMES, written, "the written keys are the table's names, no more, no less");
    assertEquals(2L, parameters.get("spaceMembersCount"), "distinct members, as a long");
    assertEquals(Long.class, parameters.get("spaceMembersCount").getClass());
    assertEquals(Long.class, parameters.get("spaceCreatedTime").getClass());
    assertEquals(Long.class, parameters.get("spacePendingCount").getClass());
    assertEquals(0L, parameters.get("spaceRedactorsCount"));
    assertEquals(Boolean.TRUE, parameters.get("spaceSovereign"));
    assertEquals("private", parameters.get("spaceVisibility"));
    assertEquals("7", parameters.get("parentSpaceId"), "an id is a keyword");
    assertEquals("3", parameters.get("spaceTemplateId"));
    assertEquals(List.of("5", "6"), List.copyOf(data.getListParameters().get("spaceCategoryIds")));
  }

  @Test
  void addSpaceStatisticsWritesNothingWithoutASpace() {
    StatisticData data = new StatisticData();
    data.setOperation("pageDisplay");

    AnalyticsUtils.addSpaceStatistics(data, null);

    assertEquals(0L, data.getSpaceId());
    assertNull(data.getParameters());
  }

  /**
   * The reserved names are built from the field constants: every fixed field
   * the storage writes but the space, the origin and the virtual field, with
   * their alternative names. The space family and the Documents app's
   * {@code origin} are not reserved.
   */
  @Test
  void reservedParameterNamesCoverTheFixedFieldsAndTheirVariants() {
    for (String name : List.of("id", "timestamp", "userId", "module", "subModule", "operation", "status",
                               "errorCode", "errorMessage", "duration", "isAnalytics",
                               AnalyticsUtils.STATISTIC_ORIGIN, CollaborativeActivityService.FIELD_NAME)) {
      assertTrue(AnalyticsUtils.RESERVED_PARAMETER_NAMES.matcher(name).matches(), name);
      assertTrue(AnalyticsUtils.RESERVED_PARAMETER_NAMES.matcher(name + "_alt").matches(), name + "_alt");
      assertTrue(AnalyticsUtils.RESERVED_PARAMETER_NAMES.matcher(name + "_alt3").matches(), name + "_alt3");
    }
    for (String name : List.of("spaceId", "spacePrettyName", "origin", "moduleName", "watcher", "portalUri", "count")) {
      assertFalse(AnalyticsUtils.RESERVED_PARAMETER_NAMES.matcher(name).matches(), name);
    }
    assertTrue(AnalyticsUtils.ALTERNATIVE_PARAMETER_NAMES.matcher("spaceMembersCount_alt").matches());
    assertTrue(AnalyticsUtils.ALTERNATIVE_PARAMETER_NAMES.matcher("activityType_alt2").matches());
    assertFalse(AnalyticsUtils.ALTERNATIVE_PARAMETER_NAMES.matcher("alternative").matches());
    assertTrue(AnalyticsUtils.SPACE_PARAMETER_NAMES.matcher("spaceId").matches());
    assertTrue(AnalyticsUtils.SPACE_PARAMETER_NAMES.matcher("spacePrettyName_alt1").matches());
    assertFalse(AnalyticsUtils.SPACE_PARAMETER_NAMES.matcher("spaceMembersCount").matches());
  }

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

    JsonNode merged = AnalyticsUtils.getJsonNode(AnalyticsUtils.sortByAnalyticsDate(mappings, "analytics"),
                                                 0,
                                                 null,
                                                 "mappings",
                                                 "properties");

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
    assertTrue(AnalyticsUtils.sortByAnalyticsDate(mappings, null).has("analytics_2026-09-10"), "a null prefix means the default");
    assertTrue(AnalyticsUtils.sortByAnalyticsDate(mappings, " ").has("analytics_2026-09-10"), "a blank prefix means the default");
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
