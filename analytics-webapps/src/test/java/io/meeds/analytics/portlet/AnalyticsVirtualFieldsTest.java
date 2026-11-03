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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import io.meeds.analytics.api.service.AnalyticsService;
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

  @Test
  void theMappingsResourceAnswersTheDrawerWithTheVirtualField() throws PortletException, IOException {
    AnalyticsService analyticsService = mock(AnalyticsService.class);
    when(analyticsService.retrieveMapping(false)).thenReturn(new HashSet<>(Set.of(new StatisticFieldMapping("module",
                                                                                                             "keyword",
                                                                                                             false))));
    ResourceRequest request = mock(ResourceRequest.class);
    when(request.getParameter("operation")).thenReturn("GET_MAPPINGS");
    ResourceResponse response = mock(ResourceResponse.class);
    StringWriter answer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(answer));

    new PortletUnderTest(analyticsService).serveResource(request, response);

    JSONArray fields = new JSONArray(answer.toString());
    JSONObject virtualField = null;
    for (int i = 0; i < fields.length(); i++) {
      if (CollaborativeActivityService.FIELD_NAME.equals(fields.getJSONObject(i).getString("name"))) {
        virtualField = fields.getJSONObject(i);
      }
    }
    assertEquals(2, fields.length(), "the view's entry and the virtual one");
    assertEquals("keyword", virtualField.getString("type"), "what the resource writes is what the drawer reads");
    assertTrue(virtualField.getBoolean("aggregation"));
    assertFalse(virtualField.getBoolean("scriptedField"));
  }

  /** The smallest portlet: the resource under test is final in the base class. */
  private static final class PortletUnderTest extends AbstractAnalyticsPortlet<Object> {

    private final AnalyticsService analyticsService;

    PortletUnderTest(AnalyticsService analyticsService) {
      this.analyticsService = analyticsService;
    }

    @Override
    protected AnalyticsService getAnalyticsService() {
      return analyticsService;
    }

    @Override
    protected String getViewPagePath() {
      return null;
    }

    @Override
    protected Class<Object> getFilterClass() {
      return Object.class;
    }

    @Override
    protected void readSettings(ResourceRequest request, ResourceResponse response) {
      // not under test
    }

    @Override
    protected void readData(ResourceRequest request, ResourceResponse response) {
      // not under test
    }
  }

}
