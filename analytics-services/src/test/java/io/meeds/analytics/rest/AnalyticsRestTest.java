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
package io.meeds.analytics.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.chart.ChartAggregationResult;
import io.meeds.analytics.model.chart.ChartData;
import io.meeds.analytics.model.chart.ChartDataList;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDeclaration;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDefinition;
import io.meeds.analytics.model.collaborative.CollaborativeActivityLabels;
import io.meeds.analytics.model.filter.aggregation.AnalyticsAggregationType;
import io.meeds.analytics.rest.model.CollaborativeActivityHelp;

/**
 * The REST parameters of the collaborative activity: they reach the service,
 * a partial chart answer becomes a 503 only when asked, the samples endpoint
 * no longer requires the sort it never applies, and the help endpoint is not
 * cached by the browser. The controllers are called directly, with the
 * binding checked on the annotations: no Spring context is booted.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsRestTest {

  private static final java.util.Set<String> OPTIONAL_SAMPLE_PARAMETERS = java.util.Set.of("sortBy", "sortDirection", "collaborativeOnly");

  @Mock
  private AnalyticsService              analyticsService;

  @Mock
  private CollaborativeActivityService  collaborativeActivityService;

  @InjectMocks
  private AnalyticsSampleRest           sampleRest;

  @InjectMocks
  private AnalyticsChartRest            chartRest;

  @InjectMocks
  private CollaborativeActivityRest     collaborativeActivityRest;

  @Test
  void collaborativeOnlyReachesTheSamplesService() {
    StatisticData sample = new StatisticData();
    when(analyticsService.getSamples(any(), any(), any(), any(), any(), anyInt(), eq(true))).thenReturn(List.of(sample));

    List<StatisticData> samples = sampleRest.getSamples(null, "spaceId", List.of("42"), null, null, 1, true);

    assertEquals(List.of(sample), samples);
    verify(analyticsService).getSamples(null, "spaceId", List.of("42"), null, null, 1, true);
  }

  @Test
  void theSamplesSortParametersAreOptionalAndTheDescriptionSaysNewestFirst() throws NoSuchMethodException {
    Method getSamples = AnalyticsSampleRest.class.getMethod("getSamples",
                                                            List.class,
                                                            String.class,
                                                            List.class,
                                                            String.class,
                                                            String.class,
                                                            int.class,
                                                            boolean.class);
    // Spring's merged view applies the value/name alias; plain reflection
    // would read an empty name for @RequestParam("x") and skip the check.
    int optionalChecked = 0;
    for (Parameter parameter : getSamples.getParameters()) {
      RequestParam requestParam = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestParam.class);
      assertNotNull(requestParam, parameter.getName());
      if (OPTIONAL_SAMPLE_PARAMETERS.contains(requestParam.name())) {
        assertFalse(requestParam.required(), requestParam.name() + " is optional");
        optionalChecked++;
      }
    }
    assertEquals(OPTIONAL_SAMPLE_PARAMETERS.size(), optionalChecked, "every optional parameter was found and checked");
    assertTrue(getSamples.getAnnotation(io.swagger.v3.oas.annotations.Operation.class).description().contains("newest first"));
  }

  @Test
  void collaborativeOnlyReachesTheChartServiceAndTheAnswerIsTheAggregationResults() {
    ChartDataList chartData = chartWithFailedShards(0);
    when(analyticsService.getChart(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyBoolean()))
                                                                                                                         .thenReturn(chartData);

    List<ChartAggregationResult> results = chartRest.getChart(null, null, null, "spaceId", AnalyticsAggregationType.TERMS,
                                                              "desc", "timestamp", AnalyticsAggregationType.MAX, "desc", 10,
                                                              true, false);

    assertEquals(1, results.size());
    verify(analyticsService).getChart(null, null, null, "spaceId", AnalyticsAggregationType.TERMS, "desc", "timestamp",
                                      AnalyticsAggregationType.MAX, "desc", 10, true);
  }

  @Test
  void aPartialAnswerIsA503OnlyWhenEveryShardIsRequired() {
    when(analyticsService.getChart(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyBoolean()))
                                                                                                                         .thenReturn(chartWithFailedShards(1));

    ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                                                   () -> chartRest.getChart(null, null, null, "spaceId",
                                                                            AnalyticsAggregationType.TERMS, "desc",
                                                                            "timestamp", AnalyticsAggregationType.MAX,
                                                                            "desc", 10, false, true));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, refused.getStatusCode());
    assertEquals("analytics.chart.partialAnswer", refused.getReason(), "a message code, never the engine's text");

    List<ChartAggregationResult> results = chartRest.getChart(null, null, null, "spaceId", AnalyticsAggregationType.TERMS,
                                                              "desc", "timestamp", AnalyticsAggregationType.MAX, "desc", 10,
                                                              false, false);
    assertEquals(1, results.size(), "without the option a partial answer is today's list");
  }

  @Test
  void theHelpListsEachDeclarationsLabelsAndIsNeverStoredByTheBrowser() throws NoSuchMethodException {
    CollaborativeActivityDeclaration tasks = new CollaborativeActivityDeclaration();
    tasks.setSource("jar:file:/lib/task-services.jar!/analytics-collaborative-activities.json");
    tasks.setModules(List.of("tasks"));
    tasks.setOperations(List.of("taskCreated"));
    tasks.setLabels(new CollaborativeActivityLabels(List.of("task.help.create")));
    CollaborativeActivityDeclaration kudos = new CollaborativeActivityDeclaration();
    when(collaborativeActivityService.getDefinition()).thenReturn(new CollaborativeActivityDefinition(List.of(tasks, kudos),
                                                                                                      List.of("skipped"),
                                                                                                      List.of(),
                                                                                                      List.of()));

    ResponseEntity<List<CollaborativeActivityHelp>> response = collaborativeActivityRest.getHelp();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    String cacheControl = response.getHeaders().getCacheControl();
    assertNotNull(cacheControl);
    assertTrue(cacheControl.contains("no-store"), cacheControl);
    assertTrue(cacheControl.contains("private"), cacheControl);
    List<CollaborativeActivityHelp> help = response.getBody();
    assertNotNull(help);
    assertEquals(2, help.size(), "one entry per installed addon, an empty declaration included");
    assertEquals(List.of("tasks"), help.get(0).modules());
    assertEquals(List.of("task.help.create"), help.get(0).counted());
    assertTrue(help.get(1).counted().isEmpty());
    assertFalse(response.toString().contains("task-services.jar"), "the declaration's source stays on the server");
    assertFalse(response.toString().contains("taskCreated"), "operation names stay on the server");
    Secured secured = CollaborativeActivityRest.class.getMethod("getHelp").getAnnotation(Secured.class);
    assertNotNull(secured);
    assertEquals("users", secured.value()[0], "any logged-in user reads the help");
  }

  private static ChartDataList chartWithFailedShards(int failedShards) {
    ChartDataList chartData = new ChartDataList("en");
    List<ChartAggregationResult> results = new ArrayList<>();
    results.add(new ChartAggregationResult(null, "42", "1700000000000"));
    chartData.getCharts().add(new ChartData(null, results, "en", null));
    chartData.setFailedShards(failedShards);
    return chartData;
  }

}
