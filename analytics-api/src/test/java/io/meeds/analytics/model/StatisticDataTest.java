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
package io.meeds.analytics.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class StatisticDataTest {

  @Test
  void removeParametersDropsTheMatchingNamesFromBothMapsAndResetsTheId() {
    StatisticData data = new StatisticData();
    data.setOperation("pageDisplay");
    data.addKeyword("module_alt", "tasks");
    data.addKeyword("origin", "drawer");
    data.addKeywords("spaceId_alt2", List.of("1", "2"));
    data.addKeywords("tags", List.of("a"));
    long idBefore = data.computeId();

    List<String> removed = data.removeParameters(Pattern.compile(".*_alt\\d*$"));

    assertEquals(Set.of("module_alt", "spaceId_alt2"), Set.copyOf(removed));
    assertFalse(data.getParameters().containsKey("module_alt"));
    assertTrue(data.getParameters().containsKey("origin"), "a name the pattern does not match stays");
    assertFalse(data.getListParameters().containsKey("spaceId_alt2"));
    assertTrue(data.getListParameters().containsKey("tags"));
    assertNotEquals(idBefore, data.computeId(), "the id is computed from the parameters, so it changes");
  }

  @Test
  void removeParametersMatchesTheWholeNameAndTolerateAbsentMaps() {
    StatisticData data = new StatisticData();
    data.setOperation("pageDisplay");

    assertTrue(data.removeParameters(Pattern.compile("^module$")).isEmpty(), "no map yet: nothing removed, no error");

    data.addKeyword("moduleName", "x");
    data.addKeyword("module", "y");
    List<String> removed = data.removeParameters(Pattern.compile("^module$"));

    assertEquals(List.of("module"), removed, "the match is on the whole name, so moduleName stays");
    assertTrue(data.getParameters().containsKey("moduleName"));
  }

  @Test
  void getKeywordReadsAParameterAsAStringAndNothingElse() {
    StatisticData data = new StatisticData();
    assertNull(data.getKeyword("statisticOrigin"), "no map yet");

    data.addKeyword("statisticOrigin", "websocket");
    data.addLong("count", 12);
    data.addKeywords("tags", List.of("a", "b"));

    assertEquals("websocket", data.getKeyword("statisticOrigin"));
    assertEquals("12", data.getKeyword("count"), "a typed parameter is read back as its string");
    assertNull(data.getKeyword("tags"), "a list parameter is not a keyword");
    assertNull(data.getKeyword("absent"));
  }

}
