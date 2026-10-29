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
package io.meeds.analytics.model.collaborative;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.ToString;

/**
 * The merged view of every addon's declaration, read once at startup. The
 * derived sets are what the filter clause and the per-event rule read; the
 * declarations are kept for the help, which lists them per addon.
 */
@Getter
@ToString
public class CollaborativeActivityDefinition implements Serializable {

  private static final long                          serialVersionUID = 4868120503251976433L;

  /** The declarations loaded, in classpath order, one per valid file */
  private final List<CollaborativeActivityDeclaration> declarations;

  /**
   * Every declared operation and legacy operation emitted from the server,
   * the stream operations Social declares included
   */
  private final Set<String>                           serverOperations;

  /**
   * The activity types a stream post or comment may carry without being a
   * collaborative act
   */
  private final Set<String>                           excludedActivityTypes;

  /** Every declared browser operation, with the module it is stored under */
  private final Map<String, String>                   browserOperations;

  /** The declaration files skipped as structurally invalid */
  private final List<String>                          skippedFiles;

  /** The unknown keys ignored, as {@code <source>: <key>} */
  private final List<String>                          ignoredKeys;

  /**
   * The browser operation entries skipped for a missing module, as
   * {@code <source>: <operation>}
   */
  private final List<String>                          skippedBrowserOperations;

  public CollaborativeActivityDefinition(List<CollaborativeActivityDeclaration> declarations,
                                         List<String> skippedFiles,
                                         List<String> ignoredKeys,
                                         List<String> skippedBrowserOperations) {
    this.declarations = List.copyOf(declarations);
    this.skippedFiles = List.copyOf(skippedFiles);
    this.ignoredKeys = List.copyOf(ignoredKeys);
    this.skippedBrowserOperations = List.copyOf(skippedBrowserOperations);
    Set<String> operations = new LinkedHashSet<>();
    Set<String> excludedTypes = new LinkedHashSet<>();
    Map<String, String> browserOperationModules = new LinkedHashMap<>();
    for (CollaborativeActivityDeclaration declaration : this.declarations) {
      operations.addAll(declaration.getOperations());
      operations.addAll(declaration.getLegacyOperations());
      excludedTypes.addAll(declaration.getExcludedActivityTypes());
      declaration.getBrowserOperations()
                 .forEach(browserOperation -> browserOperationModules.putIfAbsent(browserOperation.getOperation(),
                                                                                  browserOperation.getModule()));
    }
    this.serverOperations = Collections.unmodifiableSet(operations);
    this.excludedActivityTypes = Collections.unmodifiableSet(excludedTypes);
    this.browserOperations = Collections.unmodifiableMap(browserOperationModules);
  }

  /**
   * @return an empty definition: nothing declared, nothing skipped
   */
  public static CollaborativeActivityDefinition empty() {
    return new CollaborativeActivityDefinition(List.of(), List.of(), List.of(), List.of());
  }

}
