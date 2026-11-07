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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class StatisticDataTest {

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
