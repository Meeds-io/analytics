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
 * The i18n keys of an addon's user-facing actions, one entry per action: the
 * actions that count as a collaborative activity and the ones that do not.
 * The texts live in the addon's own {@code Portlets} bundle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollaborativeActivityLabels implements Serializable {

  private static final long serialVersionUID = 7325884139162040785L;

  private List<String>      counted          = new ArrayList<>();

  private List<String>      notCounted       = new ArrayList<>();

}
