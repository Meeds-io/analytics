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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;

import io.meeds.analytics.model.chart.TableColumnItemValue;
import io.meeds.analytics.model.chart.TableColumnResult;
import io.meeds.analytics.model.filter.AnalyticsTableColumnAggregation;
import io.meeds.analytics.model.filter.AnalyticsTableColumnFilter;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregation;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;

/**
 * Pins that the Users table lists user identities only: a statistic recorded
 * with a space identity as its userId (gamification does it) must not become
 * a row of blank cells.
 */
class AnalyticsTableUserRowsTest {

  private static Identity identity(String providerId) {
    Identity identity = new Identity(providerId, "remote");
    return identity;
  }

  private static final Map<String, Identity> IDENTITIES = Map.of("1", identity(OrganizationIdentityProvider.NAME),
                                                                 "2", identity(SpaceIdentityProvider.NAME),
                                                                 "3", identity(OrganizationIdentityProvider.NAME),
                                                                 "4", identity(SpaceIdentityProvider.NAME),
                                                                 "5", identity(OrganizationIdentityProvider.NAME),
                                                                 "6", identity(OrganizationIdentityProvider.NAME));

  private static final Function<String, Identity> RESOLVER = IDENTITIES::get;

  private static TableColumnResult rows(String... keys) {
    TableColumnResult result = new TableColumnResult();
    result.setItems(new ArrayList<>());
    for (String key : keys) {
      result.addItem(new TableColumnItemValue(key, key, null, null, null));
    }
    return result;
  }

  private static List<String> keys(TableColumnResult result) {
    return result.getItems().stream().map(TableColumnItemValue::getKey).toList();
  }

  private static AnalyticsTableColumnFilter column(String field, AnalyticsAggregationType type) {
    AnalyticsTableColumnFilter column = new AnalyticsTableColumnFilter();
    AnalyticsTableColumnAggregation valueAggregation = new AnalyticsTableColumnAggregation();
    valueAggregation.setAggregation(new AnalyticsAggregation(type, field, null, null, 0));
    column.setValueAggregation(valueAggregation);
    return column;
  }

  @Test
  void testSpaceIdentitiesAreRemovedFromTheRows() {
    TableColumnResult result = rows("1", "2", "3");
    assertEquals(2, AnalyticsTablePortlet.removeSpaceRows(result, RESOLVER));
    assertEquals(List.of("1", "3"), keys(result));
  }

  @Test
  void testAnUnresolvableIdentityIsKeptSoThatTheTableStatesItIsUnknown() {
    TableColumnResult result = rows("1", "999");
    AnalyticsTablePortlet.removeSpaceRows(result, RESOLVER);
    assertEquals(List.of("1", "999"), keys(result));
  }

  @Test
  void testThePageStaysFullWhenSpacesTakeRowsOfTheRequestedLimit() throws Exception {
    // Elasticsearch buckets by count: rows 2 and 4 are spaces
    AnalyticsTablePortlet.ColumnFetcher fetcher = limit -> rows(java.util.Arrays.copyOf(new String[] {"2", "1", "4", "3", "5", "6"},
                                                                                   Math.min(limit, 6)));
    TableColumnResult result = AnalyticsTablePortlet.fetchUserRows(2, fetcher, RESOLVER);
    assertEquals(List.of("1", "3"), keys(result));
  }

  @Test
  void testNoMoreFetchWhenTheBucketsAreExhausted() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    AnalyticsTablePortlet.ColumnFetcher fetcher = limit -> {
      calls.incrementAndGet();
      return rows("2", "1");
    };
    TableColumnResult result = AnalyticsTablePortlet.fetchUserRows(5, fetcher, RESOLVER);
    assertEquals(List.of("1"), keys(result));
    assertEquals(1, calls.get());
  }

  @Test
  void testOnlyTheUserIdTermsMainColumnIsConcerned() {
    assertTrue(AnalyticsTablePortlet.isUserIdentityColumn(column("userId", AnalyticsAggregationType.TERMS)));
    assertFalse(AnalyticsTablePortlet.isUserIdentityColumn(column("spaceId", AnalyticsAggregationType.TERMS)));
    assertFalse(AnalyticsTablePortlet.isUserIdentityColumn(column("userId", AnalyticsAggregationType.CARDINALITY)));
    assertFalse(AnalyticsTablePortlet.isUserIdentityColumn(null));
  }

}
