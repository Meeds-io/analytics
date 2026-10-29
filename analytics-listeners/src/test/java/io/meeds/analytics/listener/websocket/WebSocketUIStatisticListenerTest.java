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
package io.meeds.analytics.listener.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.stubbing.Answer;
import org.mockito.quality.Strictness;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.api.websocket.AnalyticsWebSocketService;
import io.meeds.analytics.model.AnalyticsWebSocketMessage;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.StatisticWatcher;
import io.meeds.analytics.utils.AnalyticsUtils;

/**
 * The websocket path is a trust boundary: what a page sends decides nothing
 * the server writes. Each rule is pinned with a value a page would send to
 * forge an event, and with its comma-separated form, which the listener
 * stores as a list.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketUIStatisticListenerTest {

  private static final String           USER          = "john";

  private static final Set<String>      TYPED_FIELDS  = Set.of("pageLoadMillis", "count");

  @Mock
  private AnalyticsService              analyticsService;

  @Mock
  private CollaborativeActivityService  collaborativeActivityService;

  @Mock
  private SpaceService                  spaceService;

  @Mock
  private ListenerService               listenerService;

  @InjectMocks
  private WebSocketUIStatisticListener  listener;

  private MockedStatic<AnalyticsUtils>  utils;

  private Space                         spaceA;

  private Space                         spaceB;

  private Space                         spaceC;

  @BeforeEach
  void setUp() {
    spaceA = space("1", "a", USER, "jane");
    spaceB = space("2", "b", USER);
    spaceC = space("3", "c", "jane");
    // The four container-bound helpers are answered by the default answer, so
    // no stubbing call ever runs the real method (which would boot a Kernel).
    Answer<Object> helpers = invocation -> switch (invocation.getMethod().getName()) {
      case "getUserIdentityId" -> USER.equals(invocation.getArgument(0)) ? 7L : 0L;
      case "getSpaceById" -> byId(invocation.getArgument(0));
      case "getSpaceByPrettyName" -> byPrettyName(invocation.getArgument(0));
      case "addStatisticData" -> null;
      default -> invocation.callRealMethod();
    };
    utils = mockStatic(AnalyticsUtils.class, helpers);
    when(analyticsService.isKeywordField(anyString())).thenAnswer(invocation -> !TYPED_FIELDS.contains(invocation.<String> getArgument(0)));
    when(collaborativeActivityService.isBrowserOperation("sendMessage")).thenReturn(true);
    when(collaborativeActivityService.getBrowserOperationModule("sendMessage")).thenReturn("Chat");
    when(spaceService.isMember(any(Space.class), anyString()))
                                                              .thenAnswer(invocation -> Arrays.asList(invocation.<Space> getArgument(0)
                                                                                                                .getMembers())
                                                                                              .contains(invocation.<String> getArgument(1)));
  }

  @AfterEach
  void tearDown() {
    utils.close();
  }

  @Test
  void aPageEventKeepsItsOwnParametersAndGetsTheOrigin() throws Exception {
    StatisticData stored = recorded(message("pageDisplay", null, null, Map.of("deviceType", "mobile", "origin", "drawer")));

    assertEquals("portal", stored.getModule());
    assertEquals("ui", stored.getSubModule());
    assertEquals("pageDisplay", stored.getOperation());
    assertEquals(7L, stored.getUserId());
    assertEquals(0L, stored.getSpaceId());
    assertEquals("mobile", stored.getKeyword("deviceType"));
    assertEquals("drawer", stored.getKeyword("origin"), "the Documents app's origin parameter is kept");
    assertEquals("websocket", stored.getKeyword(AnalyticsUtils.STATISTIC_ORIGIN));
    assertEquals("/portal/x", stored.getKeyword("portalUri"));
  }

  @Test
  void anUnknownUserRecordsNothing() throws Exception {
    AnalyticsWebSocketMessage message = message("pageDisplay", null, null, Map.of());
    message.setUserName("nobody");

    listener.onEvent(new Event<>("x", mock(AnalyticsWebSocketService.class), message));

    utils.verify(() -> AnalyticsUtils.addStatisticData(any()), never());
  }

  @Test
  void theSpaceIsResolvedOnceFromEveryPlaceThePageNamesIt() throws Exception {
    assertEquals(1L, recorded(message("pageDisplay", "1", null, params("spaceId", "1"))).getSpaceId(), "the same space named twice");
    assertEquals(1L, recorded(message("pageDisplay", "1", "a", Map.of())).getSpaceId(), "by id and by pretty name");
    assertEquals(2L, recorded(message("pageDisplay", null, null, params("spaceId", "2"))).getSpaceId(), "the parameter alone");

    assertEquals(0L, recorded(message("pageDisplay", "1", null, params("spaceId", "2"))).getSpaceId(), "two different spaces");
    assertEquals(0L, recorded(message("sendMessage", "1", null, params("spaceId", "2"))).getSpaceId(),
                 "two different spaces, on a declared browser operation of a member of both");
    assertEquals(0L, recorded(message("pageDisplay", "9", null, Map.of())).getSpaceId(), "an unknown id");
    assertEquals(0L, recorded(message("pageDisplay", null, null, params("spaceId", "1,2"))).getSpaceId(), "a list");
    assertEquals(0L, recorded(message("sendMessage", null, null, params("spaceId", "1,2"))).getSpaceId(), "a list, as the chat sends");
    assertEquals(0L, recorded(message("pageDisplay", "1", null, params("spaceId", null))).getSpaceId(), "a JSON null");
    assertEquals(0L, recorded(message("sendMessage", null, null, params("spaceId", null))).getSpaceId(), "a direct message");
    assertEquals(0L, recorded(message("pageDisplay", null, null, params("spaceId_alt", "1"))).getSpaceId(), "a variant name");
  }

  @Test
  void theResolvedSpaceIsWrittenWithItsRealAttributesAndThePageSentOnesAreDropped() throws Exception {
    StatisticData stored = recorded(message("pageDisplay", "1", null, params("spaceMembersCount", "9999999",
                                                                           "spaceVisibility", "open",
                                                                           "spaceCreatedTime", "1",
                                                                           "spaceId", "1")));

    assertEquals(1L, stored.getSpaceId());
    assertEquals(2L, stored.getParameters().get("spaceMembersCount"), "the server's count, as a long");
    assertEquals("private", stored.getKeyword("spaceVisibility"));
    assertEquals(1700000000000L, stored.getParameters().get("spaceCreatedTime"));
    assertFalse(stored.getParameters().containsKey("spaceId"), "the space family is dropped once resolved");

    StatisticData withParameterOnly = recorded(message("pageDisplay", null, null, params("spaceId", "2",
                                                                                       "spaceMembersCount", "9999999")));
    assertEquals(2L, withParameterOnly.getSpaceId());
    assertEquals(1L, withParameterOnly.getParameters().get("spaceMembersCount"));

    StatisticData withList = recorded(message("pageDisplay", "1", null, params("spaceMembersCount", "1,2")));
    assertNull(withList.getListParameters(), "the list form is dropped too");
    assertEquals(2L, withList.getParameters().get("spaceMembersCount"));

    StatisticData noSpace = recorded(message("pageDisplay", null, null, params("spaceMembersCount", "9999999",
                                                                             "spaceVisibility", "open")));
    assertEquals(0L, noSpace.getSpaceId());
    assertFalse(noSpace.getParameters().containsKey("spaceMembersCount"), "with no space nothing of the family is written");
    assertFalse(noSpace.getParameters().containsKey("spaceVisibility"));
  }

  @Test
  void aReservedNameNeverReplacesTheServersValue() throws Exception {
    Map<String, String> forged = params("module", "tasks", "subModule", "x", "operation", "taskCreated",
                                        "userId", "99", "timestamp", "1", "status", "1", "errorCode", "7",
                                        "duration", "9", "isAnalytics", "false", "id", "5",
                                        "statisticOrigin", "server", "collaborativeActivity", "YES",
                                        "module_alt", "tasks", "operation_alt2", "taskCreated", "kept", "yes");

    StatisticData stored = recorded(message("pageDisplay", null, null, forged));

    assertEquals("portal", stored.getModule());
    assertEquals("ui", stored.getSubModule());
    assertEquals("pageDisplay", stored.getOperation());
    assertEquals(7L, stored.getUserId());
    assertEquals(0L, stored.getTimestamp(), "completed later by addStatisticData, never by the page");
    assertEquals(0L, stored.getDuration());
    assertEquals(0L, stored.getErrorCode());
    for (String name : List.of("module", "subModule", "operation", "userId", "timestamp", "status", "errorCode", "duration",
                               "isAnalytics", "id", "collaborativeActivity", "module_alt", "operation_alt2")) {
      assertFalse(stored.getParameters().containsKey(name), name + " is reserved");
    }
    assertEquals("websocket", stored.getKeyword("statisticOrigin"), "the origin is the server's, not the page's");
    assertEquals("yes", stored.getKeyword("kept"));

    Map<String, String> asLists = new HashMap<>();
    forged.forEach((name, value) -> asLists.put(name, value + "," + value));
    StatisticData storedLists = recorded(message("pageDisplay", null, null, asLists));
    for (String name : forged.keySet()) {
      if (!"kept".equals(name)) {
        assertTrue(storedLists.getListParameters() == null || !storedLists.getListParameters().containsKey(name), name);
      }
    }
    assertEquals("websocket", storedLists.getKeyword("statisticOrigin"));
    assertEquals(List.of("yes", "yes"), List.copyOf(storedLists.getListParameters().get("kept")));
  }

  @Test
  void everyAlternativeNameIsDroppedWhateverTheOperation() throws Exception {
    StatisticData stored = recorded(message("pageDisplay", null, null, params("activityType_alt", "x",
                                                                            "spaceMembersCount_alt3", "9",
                                                                            "deviceType", "mobile")));

    assertFalse(stored.getParameters().containsKey("activityType_alt"));
    assertFalse(stored.getParameters().containsKey("spaceMembersCount_alt3"));
    assertEquals("mobile", stored.getKeyword("deviceType"));
  }

  @Test
  void aDeclaredBrowserOperationIsStoredUnderItsModuleAndWithTheSpaceOfAMemberOnly() throws Exception {
    AnalyticsWebSocketMessage asTasks = message("sendMessage", "1", null, Map.of());
    asTasks.setModule("tasks");
    StatisticData member = recorded(asTasks);
    assertEquals("Chat", member.getModule(), "the declared module, whatever the page sends");
    assertEquals(1L, member.getSpaceId(), "a member keeps the space");
    assertEquals(2L, member.getParameters().get("spaceMembersCount"));

    StatisticData parameterOnly = recorded(message("sendMessage", null, null, params("spaceId", "2", "module", "tasks")));
    assertEquals("Chat", parameterOnly.getModule());
    assertEquals(2L, parameterOnly.getSpaceId());

    StatisticData notMember = recorded(message("sendMessage", "3", null, Map.of()));
    assertEquals("Chat", notMember.getModule());
    assertEquals(0L, notMember.getSpaceId(), "a non-member's event is stored without a space");
    assertFalse(notMember.getParameters().containsKey("spaceMembersCount"));
    assertEquals(0L, recorded(message("sendMessage", null, null, params("spaceId", "3"))).getSpaceId(),
                 "the space named as a parameter, from a non-member");
    assertEquals(0L, recorded(message("sendMessage", null, "c", Map.of())).getSpaceId(),
                 "the space named by its pretty name, from a non-member");
    assertEquals(0L, recorded(message("sendMessage", null, null, params("spaceId", "3,1"))).getSpaceId(),
                 "a list naming the non-member's space");
    assertEquals(0L, recorded(message("sendMessage", "3", null, params("spaceId", null))).getSpaceId(),
                 "a JSON null beside the non-member's space");
    assertEquals(0L, recorded(message("sendMessage", null, null, params("spaceId_alt", "3"))).getSpaceId(),
                 "a variant name, dropped before the resolution");

    AnalyticsWebSocketMessage undeclared = message("pageDisplay", "3", null, Map.of());
    StatisticData undeclaredStored = recorded(undeclared);
    assertEquals(3L, undeclaredStored.getSpaceId(), "an undeclared operation keeps its resolved space, no membership check");
    assertEquals("portal", undeclaredStored.getModule());
  }

  @Test
  void aParameterWhoseFieldIsNotAKeywordIsDroppedInBothForms() throws Exception {
    StatisticData stored = recorded(message("pageDisplay", null, null, params("pageLoadMillis", "12",
                                                                            "countType", "enabledUsers",
                                                                            "count", "9999999",
                                                                            "deviceType", "mobile")));
    assertFalse(stored.getParameters().containsKey("pageLoadMillis"));
    assertFalse(stored.getParameters().containsKey("count"), "a long written by the count jobs stays closed to pages");
    assertEquals("enabledUsers", stored.getKeyword("countType"));
    assertEquals("mobile", stored.getKeyword("deviceType"));

    StatisticData lists = recorded(message("pageDisplay", null, null, params("pageLoadMillis", "1,2", "tags", "a,b")));
    assertTrue(lists.getListParameters() == null || !lists.getListParameters().containsKey("pageLoadMillis"),
               "the list branch applies the rule too");
    assertEquals(List.of("a", "b"), List.copyOf(lists.getListParameters().get("tags")));
  }

  @Test
  void aWatcherSuppliesTheOperationAndItsParameters() throws Exception {
    StatisticWatcher watcher = new StatisticWatcher();
    watcher.setName("w1");
    watcher.setModule("documents");
    watcher.setOperation("viewFile");
    watcher.setParameters(Map.of("source", "drawer"));
    when(analyticsService.getUIWatcher("w1")).thenReturn(watcher);
    AnalyticsWebSocketMessage message = message(null, "1", null, params("spaceMembersCount_alt", "9"));
    message.setName("w1");
    message.setModule(null);
    message.setSubModule(null);

    StatisticData stored = recorded(message);

    assertEquals("documents", stored.getModule());
    assertEquals("ui", stored.getSubModule());
    assertEquals("viewFile", stored.getOperation());
    assertEquals("drawer", stored.getKeyword("source"));
    assertEquals("w1", stored.getKeyword("watcher"));
    assertEquals(1L, stored.getSpaceId());
    assertFalse(stored.getParameters().containsKey("spaceMembersCount_alt"), "a variant is dropped on a watcher event too");
  }

  private StatisticData recorded(AnalyticsWebSocketMessage message) throws Exception {
    utils.clearInvocations();
    listener.onEvent(new Event<>("x", mock(AnalyticsWebSocketService.class), message));
    ArgumentCaptor<StatisticData> captor = ArgumentCaptor.forClass(StatisticData.class);
    utils.verify(() -> AnalyticsUtils.addStatisticData(captor.capture()));
    return captor.getValue();
  }

  private static AnalyticsWebSocketMessage message(String operation,
                                                   String spaceId,
                                                   String spacePrettyName,
                                                   Map<String, String> parameters) {
    AnalyticsWebSocketMessage message = new AnalyticsWebSocketMessage();
    message.setUserName(USER);
    message.setModule("portal");
    message.setSubModule("ui");
    message.setOperation(operation);
    message.setSpaceId(spaceId);
    message.setSpacePrettyName(spacePrettyName);
    message.setPortalUri("/portal/x");
    message.setParameters(new HashMap<>(parameters));
    return message;
  }

  /** A parameter map that accepts a null value, as the JSON parser keeps it. */
  private static Map<String, String> params(String... namesAndValues) {
    Map<String, String> parameters = new HashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      parameters.put(namesAndValues[i], namesAndValues[i + 1]);
    }
    return parameters;
  }

  private static Space space(String id, String prettyName, String... members) {
    Space space = new Space();
    space.setId(id);
    space.setPrettyName(prettyName);
    space.setMembers(members);
    space.setVisibility("private");
    space.setRegistration("validation");
    space.setCreatedTime(1700000000000L);
    return space;
  }

  private Space byId(String id) {
    return switch (id) {
    case "1" -> spaceA;
    case "2" -> spaceB;
    case "3" -> spaceC;
    default -> null;
    };
  }

  private Space byPrettyName(String prettyName) {
    return switch (prettyName) {
    case "a" -> spaceA;
    case "c" -> spaceC;
    default -> null;
    };
  }

}
