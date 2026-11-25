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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.chart.TableColumnItemValue;
import io.meeds.analytics.model.chart.TableColumnResult;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.AnalyticsTableColumnAggregation;
import io.meeds.analytics.model.filter.AnalyticsTableColumnFilter;
import io.meeds.analytics.model.filter.AnalyticsTableFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * Pins, through the portlet's resources, that a table whose main column is the
 * {@code userId} terms never lists a space identity, that a table of spaces is
 * left alone, and that the per-viewer scope refusal still applies.
 */
class AnalyticsTablePortletUserRowsTest {

  private static final Map<String, Identity> IDENTITIES = Map.of("1", new Identity(OrganizationIdentityProvider.NAME, "john"),
                                                                 "2", new Identity(SpaceIdentityProvider.NAME, "marketing"),
                                                                 "3", new Identity(OrganizationIdentityProvider.NAME, "mary"));

  private AnalyticsService analyticsService = mock(AnalyticsService.class);

  private PortletUnderTest portlet(String mainField, AbstractAnalyticsPortlet.SearchScope scope) {
    return new PortletUnderTest(analyticsService, mainField, scope);
  }

  private void rowsAre(String... keys) {
    TableColumnResult result = new TableColumnResult();
    for (String key : keys) {
      result.addItem(new TableColumnItemValue(key, key, null, null, null));
    }
    when(analyticsService.computeTableColumnData(any(),
                                                 any(),
                                                 any(AnalyticsFilter.class),
                                                 any(),
                                                 any(),
                                                 anyInt(),
                                                 anyBoolean())).thenReturn(result);
  }

  private ResourceRequest request(String operation) {
    ResourceRequest request = mock(ResourceRequest.class);
    when(request.getParameter("operation")).thenReturn(operation);
    when(request.getParameter("column")).thenReturn("0");
    when(request.getParameter("min")).thenReturn("1000");
    when(request.getParameter("max")).thenReturn("2000");
    when(request.getParameter("limit")).thenReturn("10");
    when(request.getLocale()).thenReturn(java.util.Locale.ENGLISH);
    return request;
  }

  private List<String> readDataKeys(PortletUnderTest portlet) throws Exception {
    ResourceResponse response = mock(ResourceResponse.class);
    StringWriter answer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(answer));
    portlet.serveResource(request("GET_DATA"), response);
    JSONArray items = new JSONObject(answer.toString()).getJSONArray("items");
    return java.util.stream.IntStream.range(0, items.length()).mapToObj(i -> items.getJSONObject(i).getString("key")).toList();
  }

  @Test
  void testAUserTableDropsTheSpaceKeyFromTheData() throws Exception {
    rowsAre("2", "1", "3");
    assertEquals(List.of("1", "3"), readDataKeys(portlet("userId", AbstractAnalyticsPortlet.SearchScope.GLOBAL)));
  }

  @Test
  void testASpaceTableKeepsItsSpaceKeys() throws Exception {
    rowsAre("2", "1", "3");
    assertEquals(List.of("2", "1", "3"), readDataKeys(portlet("spaceId", AbstractAnalyticsPortlet.SearchScope.GLOBAL)));
  }

  @Test
  void testTheScopeRefusalStillRaisesAPortletException() {
    rowsAre("2", "1", "3");
    PortletException error = assertThrows(PortletException.class,
                                          () -> readDataKeys(portlet("userId", AbstractAnalyticsPortlet.SearchScope.NONE)));
    assertEquals("Not allowed to access information", error.getMessage());
  }

  @Test
  void testTheExportDropsTheSpaceRows() throws Exception {
    rowsAre("2", "1", "3");
    ResourceResponse response = mock(ResourceResponse.class);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    when(response.getPortletOutputStream()).thenReturn(out);
    ResourceRequest request = request("EXPORT_EXCEL");
    portlet("userId", AbstractAnalyticsPortlet.SearchScope.GLOBAL).serveResource(request, response);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertEquals(2, sheet.getLastRowNum(), "the header and the two user rows, the space row is not listed");
    }
  }

  private static final class PortletUnderTest extends AnalyticsTablePortlet {

    private final AnalyticsService                   service;

    private final AnalyticsTableFilter               filter = new AnalyticsTableFilter();

    private final AbstractAnalyticsPortlet.SearchScope scope;

    PortletUnderTest(AnalyticsService service, String mainField, AbstractAnalyticsPortlet.SearchScope scope) {
      this.service = service;
      this.scope = scope;
      AnalyticsTableColumnFilter main = new AnalyticsTableColumnFilter();
      AnalyticsTableColumnAggregation aggregation = new AnalyticsTableColumnAggregation();
      AnalyticsAggregation termsAggregation = new AnalyticsAggregation();
      termsAggregation.setType(AnalyticsAggregationType.TERMS);
      termsAggregation.setField(mainField);
      aggregation.setAggregation(termsAggregation);
      aggregation.setPeriodIndependent(true);
      main.setValueAggregation(aggregation);
      filter.setMainColumn(main);
    }

    @Override
    protected AnalyticsService getAnalyticsService() {
      return service;
    }

    @Override
    protected AnalyticsTableFilter getFilterFromPreferences(ResourceRequest request) {
      return filter;
    }

    @Override
    protected SearchScope getSearchScope(PortletRequest request) {
      return scope;
    }

    @Override
    protected Identity getRowIdentity(String identityId) {
      return IDENTITIES.get(identityId);
    }
  }

}
