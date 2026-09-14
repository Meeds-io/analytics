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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.social.core.space.model.Space;

/**
 * Pins the value of every space field the settings UI offers.
 * <p>
 * The switch behind these columns had cases for five fields the UI does not
 * offer and a {@code default} returning the display name, so all seven
 * offered fields exported the space name - data that looks plausible and is
 * simply the wrong column. The default is now empty, so the next unmapped
 * field is visibly missing rather than silently wrong.
 */
class AnalyticsTableSpaceFieldTest {

  private AnalyticsTablePortlet portlet;

  private Space                 space;

  @BeforeEach
  void setUp() {
    portlet = new AnalyticsTablePortlet();
    space = new Space();
    space.setDisplayName("Marketing");
    space.setDescription("The marketing space");
    space.setPrettyName("marketing");
    space.setGroupId("/spaces/marketing");
    space.setUrl("marketing");
    space.setVisibility("private");
    space.setRegistration("validation");
    space.setTemplateId(7);
    space.setCreatedTime(1789055100000L);
    space.setManagers(new String[] {"root"});
    space.setMembers(new String[] {"root", "john", "mary"});
    space.setRedactors(new String[] {});
  }

  private String value(String field) throws Exception {
    Method method = AnalyticsTablePortlet.class.getDeclaredMethod("spaceFieldValue", Space.class, String.class);
    method.setAccessible(true); // NOSONAR the method is an internal detail of the export, exercised here through its contract
    return (String) method.invoke(portlet, space, field);
  }

  @Test
  void testEveryFieldOfferedByTheSettingsUiHasItsOwnValue() throws Exception {
    // AnalyticsTableApplication.vue, spaceFields
    assertEquals("1789055100000", value("createdTime"));
    assertEquals("1", value("managersCount"));
    assertEquals("3", value("membersCount"));
    assertEquals("0", value("redactorsCount"));
    assertEquals("7", value("template"));
    assertEquals("validation", value("subscription"));
    assertEquals("private", value("visibility"));
  }

  @Test
  void testNoOfferedFieldSilentlyReturnsTheSpaceName() throws Exception {
    for (String field : new String[] {"createdTime", "managersCount", "membersCount", "redactorsCount", "template",
                                      "subscription", "visibility"}) {
      assertNotEquals("Marketing", value(field), field + " must not fall back to the space display name");
    }
  }

  @Test
  void testTheLegacyAndIdentityCasesStillResolve() throws Exception {
    assertEquals("Marketing", value("displayName"));
    assertEquals("The marketing space", value("description"));
    assertEquals("marketing", value("prettyName"));
    assertEquals("/spaces/marketing", value("groupId"));
    assertEquals("marketing", value("url"));
  }

  @Test
  void testAnUnmappedFieldIsEmptyRatherThanWrong() throws Exception {
    assertEquals("", value("somethingNobodyMapped"));
  }

  @Test
  void testNoSpaceIsAnEmptyValue() throws Exception {
    Method method = AnalyticsTablePortlet.class.getDeclaredMethod("spaceFieldValue", Space.class, String.class);
    method.setAccessible(true); // NOSONAR same as above
    assertEquals("", method.invoke(portlet, null, "visibility"));
  }

}
