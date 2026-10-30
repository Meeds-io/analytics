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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilter;
import io.meeds.analytics.model.filter.search.AnalyticsFieldFilterType;
import io.meeds.analytics.utils.AnalyticsUtils;

/**
 * The two "Most active" queries of the Spaces List Widget, declared as
 * init-params of {@code portlet.xml} and served by
 * {@link AnalyticsQueryPortlet}, rank the spaces on the collaborative
 * activities only: the filter on the virtual field replaces the former list
 * of operation names, in both configurations of the widget.
 */
class SpacesListWidgetMostActiveQueryTest {

  private static final Path PORTLET_XML = Paths.get("src/main/webapp/WEB-INF/portlet.xml");

  @Test
  void theRankingOfAllSpacesCountsTheCollaborativeActivitiesOnly() throws Exception {
    assertCollaborativeActivitiesOnly(mostActiveQuery("spacesList.mostActive"));
  }

  @Test
  void theRankingOfMemberSpacesCountsTheCollaborativeActivitiesOnly() throws Exception {
    AnalyticsFilter query = mostActiveQuery("spacesList.mostActive.memberOnly");
    assertCollaborativeActivitiesOnly(query);
    assertTrue(query.getFilters()
                    .stream()
                    .anyMatch(f -> "spaceId".equals(f.getField())
                                   && f.getType() == AnalyticsFieldFilterType.IN_SET
                                   && "{spaceMemberIds}".equals(f.getValueString())),
               "the member-only query keeps its restriction to the viewer's spaces");
  }

  private void assertCollaborativeActivitiesOnly(AnalyticsFilter query) {
    List<AnalyticsFieldFilter> filters = query.getFilters();
    assertFalse(filters.stream().anyMatch(f -> "operation".equals(f.getField())),
                "the ranking no longer names operations");
    List<AnalyticsFieldFilter> collaborative = filters.stream()
                                                      .filter(f -> CollaborativeActivityService.FIELD_NAME.equals(f.getField()))
                                                      .toList();
    assertEquals(1, collaborative.size(), "one filter on the collaborative-activity field");
    assertEquals(AnalyticsFieldFilterType.EQUAL, collaborative.get(0).getType());
    assertEquals(CollaborativeActivityService.YES, collaborative.get(0).getValueString());
    assertTrue(filters.stream().anyMatch(f -> "spaceId".equals(f.getField())),
               "the ranking still reads space events only");
    assertTrue(filters.stream().anyMatch(f -> "timestamp".equals(f.getField())
                                              && f.getType() == AnalyticsFieldFilterType.GREATER),
               "the ranking still covers the configured period");
  }

  /**
   * The init-param's JSON, parsed as the portlet parses it, after the
   * placeholders it substitutes at request time are given a value
   */
  private AnalyticsFilter mostActiveQuery(String queryName) throws Exception {
    Document portletXml = DocumentBuilderFactory.newInstance()
                                                .newDocumentBuilder()
                                                .parse(Files.newInputStream(PORTLET_XML));
    NodeList initParams = portletXml.getElementsByTagName("init-param");
    for (int i = 0; i < initParams.getLength(); i++) {
      Element initParam = (Element) initParams.item(i);
      String name = initParam.getElementsByTagName("name").item(0).getTextContent().trim();
      if (queryName.equals(name)) {
        String value = initParam.getElementsByTagName("value").item(0).getTextContent();
        return AnalyticsUtils.fromJsonString(value, AnalyticsFilter.class);
      }
    }
    throw new IllegalStateException("No init-param named " + queryName + " in " + PORTLET_XML);
  }

}
