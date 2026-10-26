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
package io.meeds.analytics.portlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.model.StatisticFieldMapping;

/**
 * The virtual collaborative-activity field is offered to the settings drawer
 * as a keyword, and only there: the mapping view the storage writes into a
 * new weekly index is left untouched, so no index ever maps the field.
 */
class AnalyticsVirtualFieldsTest {

  @Test
  void theSettingsFieldListCarriesTheVirtualFieldAsAKeyword() {
    Set<StatisticFieldMapping> view = new HashSet<>(Set.of(new StatisticFieldMapping("module", "keyword", false),
                                                           new StatisticFieldMapping("doc['timestamp'].value.year",
                                                                                     "long",
                                                                                     false,
                                                                                     true)));

    Set<StatisticFieldMapping> fields = AbstractAnalyticsPortlet.withVirtualFields(view);

    StatisticFieldMapping virtualField = fields.stream()
                                               .filter(field -> CollaborativeActivityService.FIELD_NAME.equals(field.getName()))
                                               .findFirst()
                                               .orElseThrow();
    assertEquals("keyword", virtualField.getType(), "a keyword, so the value suggester is shown for it");
    assertTrue(virtualField.isAggregation());
    assertFalse(virtualField.isScriptedField(), "not a scripted entry: it is offered as a filter field");
    assertEquals(3, fields.size());
  }

  @Test
  void theMappingViewItselfNeverHoldsTheVirtualField() {
    Set<StatisticFieldMapping> view = new HashSet<>(Set.of(new StatisticFieldMapping("module", "keyword", false)));

    AbstractAnalyticsPortlet.withVirtualFields(view);

    assertEquals(1, view.size(), "the view handed to the storage at a rollover is not the one the drawer reads");
    assertTrue(view.stream().noneMatch(field -> CollaborativeActivityService.FIELD_NAME.equals(field.getName())));
  }

}
