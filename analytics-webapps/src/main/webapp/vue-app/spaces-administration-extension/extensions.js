/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import {getLastActivityTime} from './js/lastActivity.js';
import {sortSpacesByLastActivity} from './js/lastActivitySort.js';

// The Last Activity column of Manage Spaces, registered only for a viewer
// the analytics endpoints admit: its cells, its sort and its Excel export
// read them
export function registerLastActivityColumn() {
  extensionRegistry.registerExtension('spaces-administration', 'table-column', {
    rank: 10,
    name: 'last-activity',
    titleKey: 'social.spaces.administration.manageSpaces.lastActivity',
    header: {
      value: 'lastActivityTime',
      align: 'center',
      sortable: true,
      // The header stays on one line: the table's layout is fixed, so the
      // width holds the label, the help icon, the sort arrow and their gaps
      // in the longest translation (195px in French)
      class: 'space-last-activity-header px-1 text-no-wrap',
      width: '200px',
    },
    componentName: 'spaces-administration-last-activity',
    // The "?" beside the header label, before the sort arrow, which opens
    // the "What counts as activity?" help
    headerComponentName: 'spaces-administration-last-activity-help',
    export: {
      type: 'date',
      value: getLastActivityTime,
    },
    sortBy: 'lastActivityTime',
    // Dated spaces by their last collaborative event, then the others by
    // title (js/lastActivitySort.js)
    customSort: sortSpacesByLastActivity,
  });
}
