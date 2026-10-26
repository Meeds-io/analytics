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
package io.meeds.analytics.rest.model;

import java.util.List;

/**
 * What the help drawer shows for one installed addon: the modules its events
 * belong to and the i18n keys of its counted and not counted actions. The
 * declaration's source and operation names stay on the server.
 *
 * @param modules the analytics modules of the addon's events
 * @param counted the i18n keys of the actions that count
 * @param notCounted the i18n keys of the actions that do not count
 */
public record CollaborativeActivityHelp(List<String> modules, List<String> counted, List<String> notCounted) {
}
