/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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

export const LAST_ACTIVITY_SAMPLES_OPTIONS = {
  sortBy: 'timestamp',
  sortDirection: 'desc',
  limit: 1,
};

/**
 * The last activity time of a space, shared by the Manage Spaces column and
 * its Excel export: one request per space, kept on the Manage Spaces root
 * instance as a promise, so that both wait for the same request. A failed
 * request is not kept, so that the next read requests it again.
 *
 * @param {Object} space the space, as listed by Manage Spaces
 * @param {Object} root the Manage Spaces root Vue instance
 * @returns {Promise<number>} the time in milliseconds, 0 when the space has
 *          no activity, null when the viewer may not read it (HTTP 403);
 *          rejected on any other failure
 */
export function getLastActivityTime(space, root) {
  if (!root.analyticsSamples) {
    root.analyticsSamples = {};
  }
  if (!Object.hasOwn(root.analyticsSamples, space.id)) {
    const lastActivityTime = Vue.prototype.$analyticsService.getSamples({
      ...LAST_ACTIVITY_SAMPLES_OPTIONS,
      operations: Vue.prototype.$analyticsService.SPACE_ACTIVITY_OPERATIONS,
      fieldName: 'spaceId',
      fieldValues: [space.id],
    })
      .then(samples => samples?.[0]?.timestamp || 0)
      .catch(e => {
        if (e?.status === 403) {
          return null;
        }
        if (root.analyticsSamples[space.id] === lastActivityTime) {
          delete root.analyticsSamples[space.id];
        }
        throw e;
      });
    root.analyticsSamples[space.id] = lastActivityTime;
  }
  return root.analyticsSamples[space.id];
}
