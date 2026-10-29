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
package io.meeds.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDeclaration;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDefinition;
import io.meeds.analytics.utils.AnalyticsUtils;

import jakarta.annotation.PostConstruct;

class CollaborativeActivityServiceImplTest {

  private static final String             TASK_DECLARATION    = """
      {"modules": ["tasks"],
       "operations": ["taskCreated", "taskCommented"],
       "labels": {"title": "task.help.title", "counted": ["task.help.create"]}}
      """;

  private static final String             STREAM_DECLARATION  = """
      {"modules": ["social"], "operations": ["createActivity", "createComment"]}
      """;

  private static final String             NOTES_DECLARATION   = """
      {"modules": ["contents"],
       "operations": ["createContent"],
       "legacyOperations": ["noteCreated"],
       "excludedActivityTypes": ["ks-wiki:spaces"]}
      """;

  private static final String             MATRIX_DECLARATION  = """
      {"modules": ["Chat"], "browserOperations": [{"operation": "sendMessage", "module": "Chat"}]}
      """;

  private static final String             CONTENT_DECLARATION = """
      {"operations": ["createContent"], "excludedActivityTypes": ["news"]}
      """;

  @TempDir
  Path                                    tempDir;

  private CollaborativeActivityServiceImpl service;

  private int                             fileCount;

  @BeforeEach
  void setUp() {
    service = new CollaborativeActivityServiceImpl();
  }

  @Test
  void loadReadsEveryDeclarationAndIgnoresAJarWithout() throws IOException {
    ClassLoader loader = loaderOver(TASK_DECLARATION, null, STREAM_DECLARATION);

    CollaborativeActivityDefinition definition = service.load(loader);

    assertEquals(2, definition.getDeclarations().size());
    assertEquals(Set.of("taskCreated", "taskCommented", "createActivity", "createComment"), definition.getServerOperations());
    assertTrue(definition.getSkippedFiles().isEmpty());
    assertTrue(definition.getIgnoredKeys().isEmpty());
    CollaborativeActivityDeclaration task = definition.getDeclarations().getFirst();
    assertEquals(List.of("tasks"), task.getModules());
    assertEquals("task.help.title", task.getLabels().getTitle());
    assertEquals(List.of("task.help.create"), task.getLabels().getCounted());
    assertTrue(task.getSource().endsWith(CollaborativeActivityService.DECLARATION_FILE));
  }

  @Test
  void loadMergesLegacyOperationsExcludedTypesAndBrowserOperations() throws IOException {
    CollaborativeActivityDefinition definition = service.load(loaderOver(NOTES_DECLARATION,
                                                                         MATRIX_DECLARATION,
                                                                         CONTENT_DECLARATION));

    assertEquals(Set.of("createContent", "noteCreated"), definition.getServerOperations(), "legacy names count like operations");
    assertEquals(Set.of("ks-wiki:spaces", "news"), definition.getExcludedActivityTypes());
    assertEquals(Map.of("sendMessage", "Chat"), definition.getBrowserOperations());
    assertFalse(service.isBrowserOperation("sendMessage"), "the loaded definition is not applied until init assigns it");
  }

  @Test
  void anUnknownKeyIsIgnoredWithAWarningAndTheRestOfTheFileLoads() throws IOException {
    String declaration = """
        {"operations": ["taskCreated"], "colour": "blue", "labels": {"counted": [], "tooltip": "x"},
         "browserOperations": [{"operation": "ping", "module": "Chat", "weight": 2}]}
        """;

    CollaborativeActivityDefinition definition = service.load(loaderOver(declaration));

    assertEquals(1, definition.getDeclarations().size());
    assertEquals(Set.of("taskCreated"), definition.getServerOperations());
    assertEquals(Map.of("ping", "Chat"), definition.getBrowserOperations());
    assertEquals(3, definition.getIgnoredKeys().size(), definition.getIgnoredKeys().toString());
    assertTrue(definition.getIgnoredKeys().stream().anyMatch(key -> key.endsWith(": colour")));
    assertTrue(definition.getIgnoredKeys().stream().anyMatch(key -> key.endsWith(": labels.tooltip")));
    assertTrue(definition.getIgnoredKeys().stream().anyMatch(key -> key.endsWith(": browserOperations.weight")));
    assertTrue(definition.getSkippedFiles().isEmpty());
  }

  @Test
  void aStructurallyInvalidFileIsSkippedWhileTheOthersLoad() throws IOException {
    CollaborativeActivityDefinition definition = service.load(loaderOver("{not json",
                                                                         TASK_DECLARATION,
                                                                         "{\"operations\": \"taskCreated\"}",
                                                                         "{\"labels\": [\"a\"]}",
                                                                         "{\"browserOperations\": [\"sendMessage\"]}",
                                                                         "{\"browserOperations\": [{\"module\": \"Chat\"}]}"));

    assertEquals(1, definition.getDeclarations().size(), "only the task declaration is valid");
    assertEquals(Set.of("taskCreated", "taskCommented"), definition.getServerOperations());
    assertEquals(5, definition.getSkippedFiles().size(), definition.getSkippedFiles().toString());
  }

  @Test
  void aNullWhereAValueIsExpectedSkipsTheFile() throws IOException {
    CollaborativeActivityDefinition definition = service.load(loaderOver("{\"operations\": null}",
                                                                         "{\"operations\": [\"taskCreated\", null]}",
                                                                         "{\"labels\": {\"counted\": [null]}}",
                                                                         "{\"labels\": {\"title\": null}}",
                                                                         "{\"labels\": null}",
                                                                         "{\"browserOperations\": [null]}",
                                                                         STREAM_DECLARATION));

    assertEquals(1, definition.getDeclarations().size());
    assertEquals(6, definition.getSkippedFiles().size(), definition.getSkippedFiles().toString());
    assertEquals(Set.of("createActivity", "createComment"), definition.getServerOperations());
  }

  @Test
  void aBrowserOperationWithoutAModuleIsSkippedWhileTheRestOfTheFileApplies() throws IOException {
    String declaration = """
        {"operations": ["taskCreated"],
         "browserOperations": [{"operation": "sendMessage"}, {"operation": "ping", "module": ""}, {"operation": "react", "module": "Chat"}]}
        """;

    CollaborativeActivityDefinition definition = service.load(loaderOver(declaration));

    assertEquals(1, definition.getDeclarations().size());
    assertEquals(Set.of("taskCreated"), definition.getServerOperations());
    assertEquals(Map.of("react", "Chat"), definition.getBrowserOperations());
    assertEquals(2, definition.getSkippedBrowserOperations().size());
    assertTrue(definition.getSkippedBrowserOperations().stream().anyMatch(entry -> entry.endsWith(": sendMessage")));
    assertTrue(definition.getSkippedBrowserOperations().stream().anyMatch(entry -> entry.endsWith(": ping")));
    assertTrue(definition.getSkippedFiles().isEmpty());
  }

  @Test
  void loadWithNoDeclarationGivesAnEmptyDefinitionThatMatchesNothing() throws IOException {
    CollaborativeActivityDefinition definition = service.load(loaderOver((String) null));

    assertTrue(definition.getDeclarations().isEmpty());
    StringBuilder query = new StringBuilder();
    service.appendFilterClause(query, false, List.of("activityType"));
    assertEquals("{\"match_none\":{}}", query.toString());
    assertFalse(service.isCollaborative("createActivity", null, null));
  }

  /**
   * The load reads the jars only: the service has no collaborator, so nothing
   * of it can reach Elasticsearch, and it is wired by {@link PostConstruct},
   * synchronously, on the class loader that holds analytics' own declaration.
   * The mutant that removes the annotation, or moves the load to another
   * thread, fails here.
   */
  @Test
  void initLoadsSynchronouslyFromTheClasspathAndNeedsNoCollaborator() throws Exception {
    assertTrue(CollaborativeActivityServiceImpl.class.getMethod("init").isAnnotationPresent(PostConstruct.class));
    List<String> instanceFields = new ArrayList<>();
    for (Field field : CollaborativeActivityServiceImpl.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        instanceFields.add(field.getName());
      }
    }
    assertEquals(List.of("definition"), instanceFields, "the only state is the loaded definition");

    service.init();

    CollaborativeActivityDefinition definition = service.getDefinition();
    assertTrue(definition.getServerOperations().containsAll(Set.of("createActivity", "createComment")),
               "analytics' own declaration is on the classpath: " + definition);
    assertTrue(definition.getDeclarations()
                         .stream()
                         .anyMatch(declaration -> declaration.getLabels().getCounted().size() == 2
                                                  && declaration.getModules().equals(List.of("social"))));
    assertTrue(service.isCollaborative("createActivity", null, null), "the definition is applied once init returns");
  }

  @Test
  void theClauseHoldsTheThreeBranchesOnTermsAndExistsOnly() throws IOException {
    loadAll();
    StringBuilder query = new StringBuilder();

    service.appendFilterClause(query, false, List.of("activityType"));

    String generated = query.toString();
    assertFalse(generated.contains("script"), generated);
    JSONObject clause = new JSONObject(generated);
    JSONObject bool = clause.getJSONObject("bool");
    assertEquals(1, bool.getInt("minimum_should_match"));
    JSONArray should = bool.getJSONArray("should");
    assertEquals(3, should.length(), generated);

    JSONObject serverBranch = should.getJSONObject(0).getJSONObject("bool");
    assertEquals(Set.of("taskCreated", "taskCommented", "createContent", "noteCreated"),
                 terms(serverBranch.getJSONArray("filter").getJSONObject(0), AnalyticsUtils.FIELD_OPERATION),
                 "every declared server and legacy operation but the two stream ones");
    assertEquals(1, serverBranch.getJSONArray("must_not").length());
    assertEquals(AnalyticsUtils.STATISTIC_ORIGIN,
                 serverBranch.getJSONArray("must_not").getJSONObject(0).getJSONObject("exists").getString("field"));

    JSONObject streamBranch = should.getJSONObject(1).getJSONObject("bool");
    assertEquals(Set.of("createActivity", "createComment"),
                 terms(streamBranch.getJSONArray("filter").getJSONObject(0), AnalyticsUtils.FIELD_OPERATION));
    JSONArray streamMustNot = streamBranch.getJSONArray("must_not");
    assertEquals(3, streamMustNot.length(), streamMustNot.toString());
    assertEquals(AnalyticsUtils.STATISTIC_ORIGIN, streamMustNot.getJSONObject(0).getJSONObject("exists").getString("field"));
    assertEquals(Set.of("ks-wiki:spaces", "news"), terms(streamMustNot.getJSONObject(1), "activityType"));
    assertEquals(Set.of("ks-wiki:spaces", "news"), terms(streamMustNot.getJSONObject(2), "activityType.keyword"),
                 "both names of activityType, for the indexes mapped text with a keyword sub-field");

    assertEquals(Set.of("sendMessage"), terms(should.getJSONObject(2), AnalyticsUtils.FIELD_OPERATION),
                 "the browser branch carries no origin condition");
  }

  @Test
  void theNegatedClauseWrapsTheSameClauseInAMustNot() throws IOException {
    loadAll();
    StringBuilder positive = new StringBuilder();
    StringBuilder negative = new StringBuilder();

    service.appendFilterClause(positive, false, List.of("activityType"));
    service.appendFilterClause(negative, true, List.of("activityType"));

    JSONObject negated = new JSONObject(negative.toString());
    JSONArray mustNot = negated.getJSONObject("bool").getJSONArray("must_not");
    assertEquals(1, mustNot.length());
    assertTrue(new JSONObject(positive.toString()).similar(mustNot.getJSONObject(0)));
  }

  @Test
  void anAlternativeActivityTypeNameGetsTheSameTwoClauses() throws IOException {
    loadAll();
    StringBuilder query = new StringBuilder();

    service.appendFilterClause(query, false, List.of("activityType", "activityType_alt"));

    JSONArray streamMustNot = new JSONObject(query.toString()).getJSONObject("bool")
                                                                 .getJSONArray("should")
                                                                 .getJSONObject(1)
                                                                 .getJSONObject("bool")
                                                                 .getJSONArray("must_not");
    assertEquals(5, streamMustNot.length());
    Set<String> fields = new HashSet<>();
    for (int i = 1; i < streamMustNot.length(); i++) {
      fields.addAll(streamMustNot.getJSONObject(i).getJSONObject("terms").keySet());
    }
    assertEquals(Set.of("activityType", "activityType.keyword", "activityType_alt", "activityType_alt.keyword"), fields);
  }

  @Test
  void aBranchWithNothingDeclaredIsLeftOut() {
    service.init(); // analytics' own declaration only: the stream operations, nothing else
    StringBuilder query = new StringBuilder();

    service.appendFilterClause(query, false, List.of("activityType"));

    JSONArray should = new JSONObject(query.toString()).getJSONObject("bool").getJSONArray("should");
    assertEquals(1, should.length(), "no server operation beyond the stream ones, no browser operation: one branch");
    JSONObject streamBranch = should.getJSONObject(0).getJSONObject("bool");
    assertEquals(1, streamBranch.getJSONArray("must_not").length(), "no excluded type declared: the origin condition only");
    assertFalse(query.toString().contains("[]"), "the request never carries an empty terms list: " + query);
  }

  @Test
  void withoutTheStreamDeclarationTheStreamBranchIsAbsent() throws IOException {
    assignLoaded(TASK_DECLARATION, MATRIX_DECLARATION);
    StringBuilder query = new StringBuilder();

    service.appendFilterClause(query, false, List.of("activityType"));

    JSONArray should = new JSONObject(query.toString()).getJSONObject("bool").getJSONArray("should");
    assertEquals(2, should.length(), query.toString());
    assertFalse(query.toString().contains("createActivity"));
    assertFalse(service.isCollaborative("createActivity", null, null), "a skipped or absent stream declaration counts no post");
  }

  @Test
  void isCollaborativeAppliesTheSameRuleOnOneEvent() throws IOException {
    loadAll();

    assertTrue(service.isCollaborative("taskCreated", null, null), "a declared server operation");
    assertTrue(service.isCollaborative("noteCreated", null, null), "a legacy operation");
    assertFalse(service.isCollaborative("likeActivity", null, null), "an undeclared operation");
    assertTrue(service.isCollaborative("createActivity", null, null), "an untyped post");
    assertTrue(service.isCollaborative("createActivity", "files:spaces", null), "a typed post whose type is not excluded");
    assertFalse(service.isCollaborative("createActivity", "ks-wiki:spaces", null), "a note's stream post");
    assertFalse(service.isCollaborative("createComment", "news", null), "a comment typed with an excluded type");
    assertFalse(service.isCollaborative("createActivity", null, "websocket"), "a forged post from a page");
    assertFalse(service.isCollaborative("taskCreated", null, "websocket"), "a forged server operation from a page");
    assertTrue(service.isCollaborative("sendMessage", null, "websocket"), "a declared browser operation");
    assertTrue(service.isCollaborative("sendMessage", null, null), "a browser operation stored before the origin existed");
    assertFalse(service.isCollaborative(null, null, null));
    assertFalse(service.isCollaborative("", null, null));
  }

  @Test
  void browserOperationsAreReadFromTheDefinition() throws IOException {
    loadAll();

    assertTrue(service.isBrowserOperation("sendMessage"));
    assertEquals("Chat", service.getBrowserOperationModule("sendMessage"));
    assertFalse(service.isBrowserOperation("taskCreated"));
    assertNull(service.getBrowserOperationModule("taskCreated"));
    assertFalse(service.isBrowserOperation(null));
    assertNull(service.getBrowserOperationModule(null));
  }

  private void loadAll() throws IOException {
    assignLoaded(TASK_DECLARATION, STREAM_DECLARATION, NOTES_DECLARATION, MATRIX_DECLARATION, CONTENT_DECLARATION);
  }

  /** Loads the declarations and applies them, as init does with the classpath. */
  @SuppressWarnings("unchecked")
  private void assignLoaded(String... declarations) throws IOException {
    CollaborativeActivityDefinition definition = service.load(loaderOver(declarations));
    try {
      Field field = CollaborativeActivityServiceImpl.class.getDeclaredField("definition");
      field.setAccessible(true);
      ((AtomicReference<CollaborativeActivityDefinition>) field.get(service)).set(definition);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A class loader over one directory per declaration, each holding the file
   * under its reserved name, or nothing for a null entry (a jar without a
   * declaration). The parent is the bootstrap loader (null), so the test classpath's
   * own declaration is not seen.
   */
  private ClassLoader loaderOver(String... declarations) throws IOException {
    List<URL> urls = new ArrayList<>();
    for (String declaration : declarations) {
      Path dir = Files.createDirectories(tempDir.resolve("jar" + (++fileCount)));
      if (declaration != null) {
        Files.writeString(dir.resolve(CollaborativeActivityService.DECLARATION_FILE), declaration, StandardCharsets.UTF_8);
      }
      urls.add(dir.toUri().toURL());
    }
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  private Set<String> terms(JSONObject termsClause, String field) {
    JSONArray values = termsClause.getJSONObject("terms").getJSONArray(field);
    Set<String> set = new HashSet<>();
    for (int i = 0; i < values.length(); i++) {
      set.add(values.getString(i));
    }
    return set;
  }

}
