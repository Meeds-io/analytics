/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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
import './initComponents.js';
import {registerLastActivityColumn} from './extensions.js';
import {LAST_ACTIVITY_SAMPLES_OPTIONS} from './js/lastActivity.js';

const EXPORT_GATE = 'analytics-last-activity';

// Manage Spaces holds its export while this gate is pending: the file's
// columns are fixed when an export starts, and the column below exists only
// once the probe has answered
extensionRegistry.registerExtension('spaces-administration', 'export-gate', {
  id: EXPORT_GATE,
  pending: true,
});

// One probe per page, with the column's own request minus the space: the
// column exists only for a viewer the analytics endpoints admit (HTTP 403
// otherwise, and for an expired session)
Vue.prototype.$analyticsService.getSamples({
  ...LAST_ACTIVITY_SAMPLES_OPTIONS,
  operations: Vue.prototype.$analyticsService.SPACE_ACTIVITY_OPERATIONS,
})
  .then(() => registerLastActivityColumn())
  .catch(e => {
    if (e?.status !== 403) {
      // eslint-disable-next-line no-console
      console.error('Error checking the access to the spaces last activity', e);
    }
  })
  .finally(() => extensionRegistry.registerExtension('spaces-administration', 'export-gate', {
    id: EXPORT_GATE,
    pending: false,
  }));
