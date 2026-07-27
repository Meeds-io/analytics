/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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
package io.meeds.analytics.model.filter.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AnalyticsAggregationTest {

  @Test
  public void testGroupByTypeProperties() {
    assertEquals("terms", AnalyticsAggregationType.GROUP_BY.getAggName());
    assertTrue(AnalyticsAggregationType.GROUP_BY.isNumericResult());
    assertTrue(!AnalyticsAggregationType.GROUP_BY.isUseInterval());
    assertTrue(!AnalyticsAggregationType.GROUP_BY.isUseSort());
    assertTrue(!AnalyticsAggregationType.GROUP_BY.isUseLimit());
  }

  @Test
  public void testMinDocCountIsPreservedOnClone() {
    AnalyticsAggregation aggregation = new AnalyticsAggregation();
    aggregation.setType(AnalyticsAggregationType.GROUP_BY);
    aggregation.setField("userId");
    aggregation.setMinDocCount(7);

    AnalyticsAggregation clone = aggregation.clone();

    assertEquals(7, clone.getMinDocCount());
    assertEquals("userId", clone.getField());
    assertEquals(AnalyticsAggregationType.GROUP_BY, clone.getType());
  }

}
