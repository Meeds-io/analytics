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
package io.meeds.analytics.api.service;

import java.util.List;

import io.meeds.analytics.model.collaborative.CollaborativeActivityDefinition;

/**
 * The definition of what counts as a collaborative activity, merged from the
 * declarations each emitting addon ships as a classpath resource named
 * {@link #DECLARATION_FILE} at the root of its services jar. The definition is
 * loaded once at startup and only read afterwards; it is applied at query time,
 * through the virtual field {@link #FIELD_NAME}, and never stored with an
 * event.
 */
public interface CollaborativeActivityService {

  /** Name of the virtual field a query filters on; no document carries it */
  String FIELD_NAME                = "collaborativeActivity";

  /** The one value of the virtual field */
  String YES                       = "YES";

  /** Classpath resource name of an addon's declaration */
  String DECLARATION_FILE          = "analytics-collaborative-activities.json";

  /** Operation recorded for a stream post, declared by Social */
  String STREAM_ACTIVITY_OPERATION = "createActivity";

  /** Operation recorded for a stream comment, declared by Social */
  String STREAM_COMMENT_OPERATION  = "createComment";

  /**
   * @return the merged definition, with the declarations it was built from
   */
  CollaborativeActivityDefinition getDefinition();

  /**
   * Appends to the given query the Elasticsearch clause that selects the
   * collaborative events: one {@code bool} clause, wrapped in a
   * {@code must_not} when negated, built from {@code terms} and
   * {@code exists} conditions only.
   *
   * @param esQuery the query being built, positioned inside a {@code must}
   *          list, where the clause is appended as one more element (the
   *          caller writes the separators)
   * @param negated whether the filter selects the events that are not
   *          collaborative
   * @param activityTypeFields the names under which an event's activity type
   *          may be stored: {@code activityType} and every alternative name
   *          the mapping holds for it
   */
  void appendFilterClause(StringBuilder esQuery, boolean negated, List<String> activityTypeFields);

  /**
   * The same rule as {@link #appendFilterClause}, evaluated on one event.
   *
   * @param operation the event's operation
   * @param activityType the event's activity type, when it carries one
   * @param statisticOrigin the event's origin parameter, when it carries one
   * @return true when the event is a collaborative activity
   */
  boolean isCollaborative(String operation, String activityType, String statisticOrigin);

  /**
   * @param operation an operation name
   * @return true when an addon declares that operation as emitted from the
   *         browser through the analytics websocket
   */
  boolean isBrowserOperation(String operation);

  /**
   * @param operation a browser operation name
   * @return the module the declaring addon stores that operation under, or
   *         null when the operation is not declared as a browser operation
   */
  String getBrowserOperationModule(String operation);

}
