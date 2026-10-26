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
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One addon's declaration of its collaborative activities, the content of its
 * {@code analytics-collaborative-activities.json}. Every list is optional in
 * the file and empty when absent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollaborativeActivityDeclaration implements Serializable {

  private static final long                          serialVersionUID      = -4035119184823734013L;

  /** The resource the declaration was read from, set by the loader */
  private String                                     source;

  /** The analytics modules of the addon's events, informative only */
  private List<String>                               modules               = new ArrayList<>();

  /** The operation names that are collaborative */
  private List<String>                               operations            = new ArrayList<>();

  /**
   * Operation names the addon no longer emits but whose stored events count
   */
  private List<String>                               legacyOperations      = new ArrayList<>();

  /**
   * For the stream post and comment events, the activity types that are not a
   * user's collaborative act
   */
  private List<String>                               excludedActivityTypes = new ArrayList<>();

  /**
   * The operations the addon emits from the browser through the analytics
   * websocket, each with the module the event is stored under
   */
  private List<CollaborativeActivityBrowserOperation> browserOperations     = new ArrayList<>();

  /** The i18n keys of the counted and not counted actions, for the help */
  private CollaborativeActivityLabels                labels                = new CollaborativeActivityLabels();

}
