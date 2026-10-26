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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.analytics.api.service.CollaborativeActivityService;
import io.meeds.analytics.model.collaborative.CollaborativeActivityBrowserOperation;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDeclaration;
import io.meeds.analytics.model.collaborative.CollaborativeActivityDefinition;
import io.meeds.analytics.model.collaborative.CollaborativeActivityLabels;
import io.meeds.analytics.utils.AnalyticsUtils;

import jakarta.annotation.PostConstruct;

/**
 * Collects every {@value CollaborativeActivityService#DECLARATION_FILE} the
 * class loader sees, synchronously at startup, so that no query ever runs on
 * a partial definition. The load reads the jars only: it makes no call to
 * Elasticsearch, and a file it cannot read is skipped with a warning while
 * the others load, so the startup never fails on a declaration.
 */
@Service
public class CollaborativeActivityServiceImpl implements CollaborativeActivityService {

  private static final Log                        LOG                        =
                                                      ExoLogger.getLogger(CollaborativeActivityServiceImpl.class);

  private static final Set<String>                STREAM_OPERATIONS          = Set.of(STREAM_ACTIVITY_OPERATION,
                                                                                      STREAM_COMMENT_OPERATION);

  private static final String                     KEY_MODULES                = "modules";

  private static final String                     KEY_OPERATIONS             = "operations";

  private static final String                     KEY_LEGACY_OPERATIONS      = "legacyOperations";

  private static final String                     KEY_EXCLUDED_ACTIVITY_TYPES = "excludedActivityTypes";

  private static final String                     KEY_BROWSER_OPERATIONS     = "browserOperations";

  private static final String                     KEY_LABELS                 = "labels";

  private static final String                     KEY_COUNTED                = "counted";

  private static final String                     KEY_NOT_COUNTED            = "notCounted";

  private static final String                     KEY_OPERATION              = "operation";

  private static final String                     KEY_MODULE                 = "module";

  private static final String                     ACTIVITY_TYPE_FIELD        = "activityType";

  private static final String                     KEYWORD_SUB_FIELD_SUFFIX   = ".keyword";

  private volatile CollaborativeActivityDefinition definition                 = CollaborativeActivityDefinition.empty();

  @PostConstruct
  public void init() {
    definition = load(getClass().getClassLoader());
  }

  @Override
  public CollaborativeActivityDefinition getDefinition() {
    return definition;
  }

  @Override
  public void appendFilterClause(StringBuilder esQuery, boolean negated, List<String> activityTypeFields) {
    JSONObject clause = buildClause(activityTypeFields == null ? List.of(ACTIVITY_TYPE_FIELD) : activityTypeFields);
    if (negated) {
      clause = new JSONObject().put("bool", new JSONObject().put("must_not", new JSONArray().put(clause)));
    }
    esQuery.append(clause);
  }

  @Override
  public boolean isCollaborative(String operation, String activityType, String statisticOrigin) {
    if (StringUtils.isBlank(operation)) {
      return false;
    }
    CollaborativeActivityDefinition currentDefinition = definition;
    if (currentDefinition.getBrowserOperations().containsKey(operation)) {
      return true;
    }
    if (StringUtils.isNotBlank(statisticOrigin) || !currentDefinition.getServerOperations().contains(operation)) {
      return false;
    }
    return !STREAM_OPERATIONS.contains(operation)
           || StringUtils.isBlank(activityType)
           || !currentDefinition.getExcludedActivityTypes().contains(activityType);
  }

  @Override
  public boolean isBrowserOperation(String operation) {
    return operation != null && definition.getBrowserOperations().containsKey(operation);
  }

  @Override
  public String getBrowserOperationModule(String operation) {
    return operation == null ? null : definition.getBrowserOperations().get(operation);
  }

  /**
   * Reads every declaration the class loader holds. Package-private so that a
   * test hands a class loader over its own files.
   *
   * @param classLoader the loader whose resources are collected
   * @return the merged definition, never null
   */
  CollaborativeActivityDefinition load(ClassLoader classLoader) {
    List<URL> urls;
    try {
      urls = Collections.list(classLoader.getResources(DECLARATION_FILE));
    } catch (IOException e) {
      LOG.warn("Collaborative activity declarations could not be listed, nothing is declared", e);
      urls = List.of();
    }
    List<CollaborativeActivityDeclaration> declarations = new ArrayList<>();
    List<String> skippedFiles = new ArrayList<>();
    List<String> ignoredKeys = new ArrayList<>();
    List<String> skippedBrowserOperations = new ArrayList<>();
    for (URL url : urls) {
      String source = url.toString();
      try {
        declarations.add(parse(source, readContent(url), ignoredKeys, skippedBrowserOperations));
      } catch (IOException | JSONException | IllegalArgumentException e) {
        skippedFiles.add(source);
        LOG.warn("Collaborative activity declaration {} skipped, its content is not valid: {}", source, e.getMessage());
      }
    }
    CollaborativeActivityDefinition loaded = new CollaborativeActivityDefinition(declarations,
                                                                                 skippedFiles,
                                                                                 ignoredKeys,
                                                                                 skippedBrowserOperations);
    LOG.info("Collaborative activity definition loaded from {} declaration(s): {} server operation(s), {} excluded activity type(s), {} browser operation(s). Skipped files: {}. Ignored keys: {}. Browser operations skipped for a missing module: {}",
             loaded.getDeclarations().size(),
             loaded.getServerOperations().size(),
             loaded.getExcludedActivityTypes().size(),
             loaded.getBrowserOperations().size(),
             loaded.getSkippedFiles(),
             loaded.getIgnoredKeys(),
             loaded.getSkippedBrowserOperations());
    return loaded;
  }

  private String readContent(URL url) throws IOException {
    try (InputStream inputStream = url.openStream()) {
      return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    }
  }

  /**
   * Parses one declaration. A structural defect (a value that is not what its
   * key expects, a JSON null where a value is expected, a null entry in a
   * list) is an {@link IllegalArgumentException}: the whole file is skipped.
   * An unknown key is ignored and the rest of the file applies; a browser
   * operation entry without a module is skipped and the rest of the file
   * applies.
   */
  private CollaborativeActivityDeclaration parse(String source,
                                                 String content,
                                                 List<String> ignoredKeys,
                                                 List<String> skippedBrowserOperations) {
    JSONObject json = new JSONObject(content);
    CollaborativeActivityDeclaration declaration = new CollaborativeActivityDeclaration();
    declaration.setSource(source);
    for (String key : json.keySet()) {
      switch (key) {
      case KEY_MODULES -> declaration.setModules(stringList(json, key));
      case KEY_OPERATIONS -> declaration.setOperations(stringList(json, key));
      case KEY_LEGACY_OPERATIONS -> declaration.setLegacyOperations(stringList(json, key));
      case KEY_EXCLUDED_ACTIVITY_TYPES -> declaration.setExcludedActivityTypes(stringList(json, key));
      case KEY_BROWSER_OPERATIONS -> declaration.setBrowserOperations(browserOperations(source,
                                                                                        json,
                                                                                        ignoredKeys,
                                                                                        skippedBrowserOperations));
      case KEY_LABELS -> declaration.setLabels(labels(source, json, ignoredKeys));
      default -> ignoreKey(source, key, ignoredKeys);
      }
    }
    return declaration;
  }

  private List<String> stringList(JSONObject json, String key) {
    if (!(json.get(key) instanceof JSONArray array)) {
      throw new IllegalArgumentException("'" + key + "' must be a list of strings");
    }
    List<String> values = new ArrayList<>(array.length());
    for (int i = 0; i < array.length(); i++) {
      if (!(array.get(i) instanceof String value)) {
        throw new IllegalArgumentException("'" + key + "' must be a list of strings, entry " + i + " is not a string");
      }
      values.add(value);
    }
    return values;
  }

  private List<CollaborativeActivityBrowserOperation> browserOperations(String source,
                                                                        JSONObject json,
                                                                        List<String> ignoredKeys,
                                                                        List<String> skippedBrowserOperations) {
    if (!(json.get(KEY_BROWSER_OPERATIONS) instanceof JSONArray array)) {
      throw new IllegalArgumentException("'" + KEY_BROWSER_OPERATIONS + "' must be a list of objects");
    }
    List<CollaborativeActivityBrowserOperation> browserOperations = new ArrayList<>(array.length());
    for (int i = 0; i < array.length(); i++) {
      if (!(array.get(i) instanceof JSONObject entry)) {
        throw new IllegalArgumentException("'" + KEY_BROWSER_OPERATIONS + "' must be a list of objects, entry " + i
            + " is not an object");
      }
      if (!(entry.opt(KEY_OPERATION) instanceof String operation) || StringUtils.isBlank(operation)) {
        throw new IllegalArgumentException("'" + KEY_BROWSER_OPERATIONS + "' entry " + i + " has no operation");
      }
      for (String key : entry.keySet()) {
        if (!KEY_OPERATION.equals(key) && !KEY_MODULE.equals(key)) {
          ignoreKey(source, KEY_BROWSER_OPERATIONS + "." + key, ignoredKeys);
        }
      }
      if (entry.opt(KEY_MODULE) instanceof String module && StringUtils.isNotBlank(module)) {
        browserOperations.add(new CollaborativeActivityBrowserOperation(operation, module));
      } else {
        skippedBrowserOperations.add(source + ": " + operation);
        LOG.warn("Browser operation '{}' skipped in collaborative activity declaration {}: it names no module",
                 operation,
                 source);
      }
    }
    return browserOperations;
  }

  private CollaborativeActivityLabels labels(String source, JSONObject json, List<String> ignoredKeys) {
    if (!(json.get(KEY_LABELS) instanceof JSONObject labelsJson)) {
      throw new IllegalArgumentException("'" + KEY_LABELS + "' must be an object");
    }
    CollaborativeActivityLabels labels = new CollaborativeActivityLabels();
    for (String key : labelsJson.keySet()) {
      switch (key) {
      case KEY_COUNTED -> labels.setCounted(stringList(labelsJson, key));
      case KEY_NOT_COUNTED -> labels.setNotCounted(stringList(labelsJson, key));
      default -> ignoreKey(source, KEY_LABELS + "." + key, ignoredKeys);
      }
    }
    return labels;
  }

  private void ignoreKey(String source, String key, List<String> ignoredKeys) {
    ignoredKeys.add(source + ": " + key);
    LOG.warn("Unknown key '{}' ignored in collaborative activity declaration {}", key, source);
  }

  /**
   * The clause of the definition: one {@code bool} holding, as alternatives,
   * the declared server operations stored with no origin, the stream post and
   * comment stored with no origin and no excluded activity type under any of
   * its names, and the declared browser operations whatever their origin. A
   * branch with nothing declared is left out, and a clause with no branch
   * matches nothing. Every condition is a {@code terms} or an
   * {@code exists}, never a script.
   */
  private JSONObject buildClause(List<String> activityTypeFields) {
    CollaborativeActivityDefinition currentDefinition = definition;
    Set<String> serverOperations = new LinkedHashSet<>(currentDefinition.getServerOperations());
    Set<String> streamOperations = new LinkedHashSet<>(serverOperations);
    streamOperations.retainAll(STREAM_OPERATIONS);
    serverOperations.removeAll(STREAM_OPERATIONS);
    JSONArray should = new JSONArray();
    if (!serverOperations.isEmpty()) {
      should.put(new JSONObject().put("bool",
                                      new JSONObject().put("filter", new JSONArray().put(terms(AnalyticsUtils.FIELD_OPERATION,
                                                                                               serverOperations)))
                                                      .put("must_not", new JSONArray().put(originExists()))));
    }
    if (!streamOperations.isEmpty()) {
      JSONArray mustNot = new JSONArray().put(originExists());
      Set<String> excludedActivityTypes = currentDefinition.getExcludedActivityTypes();
      if (!excludedActivityTypes.isEmpty()) {
        for (String activityTypeField : activityTypeFields) {
          mustNot.put(terms(activityTypeField, excludedActivityTypes));
          mustNot.put(terms(activityTypeField + KEYWORD_SUB_FIELD_SUFFIX, excludedActivityTypes));
        }
      }
      should.put(new JSONObject().put("bool",
                                      new JSONObject().put("filter", new JSONArray().put(terms(AnalyticsUtils.FIELD_OPERATION,
                                                                                               streamOperations)))
                                                      .put("must_not", mustNot)));
    }
    if (!currentDefinition.getBrowserOperations().isEmpty()) {
      should.put(terms(AnalyticsUtils.FIELD_OPERATION, currentDefinition.getBrowserOperations().keySet()));
    }
    if (should.isEmpty()) {
      return new JSONObject().put("match_none", new JSONObject());
    }
    return new JSONObject().put("bool", new JSONObject().put("minimum_should_match", 1).put("should", should));
  }

  private JSONObject terms(String field, Collection<String> values) {
    return new JSONObject().put("terms", new JSONObject().put(field, new JSONArray(values)));
  }

  private JSONObject originExists() {
    return new JSONObject().put("exists", new JSONObject().put("field", AnalyticsUtils.STATISTIC_ORIGIN));
  }

}
