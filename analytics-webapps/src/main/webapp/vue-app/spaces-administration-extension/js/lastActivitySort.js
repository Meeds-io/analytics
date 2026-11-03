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

// One getSpacesByIds call carries at most this many ids, one id parameter
// each on a GET
const MAX_IDS_PER_CALL = 100;

// The page size of Social's listing walked for the spaces with no
// collaborative event
const UNDATED_PAGE_SIZE = 25;

// The sort's state for the current scroll, replaced only when a call
// returns: after a failed read, the next "load more" retries that page from
// the state the last successful call left
let sortState = null;

function newState(sortDesc) {
  return {
    sortDesc: !!sortDesc,
    // The buckets of the aggregation already read, in its order
    examinedBuckets: 0,
    // The aggregation has returned every space it holds
    datedComplete: false,
    // Every dated space id read from the aggregation (D): phase 2 skips them
    datedIds: new Set(),
    // The dated space ids read and not fetched yet, in the sort's order
    pendingIds: [],
    // The position reached in Social's listing of every space
    undatedOffset: 0,
    undatedComplete: false,
  };
}

function copyState(state) {
  return {
    ...state,
    datedIds: new Set(state.datedIds),
    pendingIds: state.pendingIds.slice(),
  };
}

/**
 * Reads the spaces grouped by id with the time of their last collaborative
 * event, in the sort's direction. requireAllShards turns an answer missing a
 * shard into a 503, so a short answer always means the aggregation is
 * complete.
 *
 * @param {object} state the scroll's state, which the read extends
 * @param {number} poolSize the count of buckets asked
 * @returns {Promise} resolved once the window is read into the state
 */
async function readDatedWindow(state, poolSize) {
  const buckets = await Vue.prototype.$analyticsService.getChart({
    collaborativeOnly: true,
    requireAllShards: true,
    xAggregationField: 'spaceId',
    xAggregationType: 'TERMS',
    yAggregationField: 'timestamp',
    yAggregationType: 'MAX',
    yAggregationSortDirection: state.sortDesc ? 'desc' : 'asc',
    limit: poolSize,
  }) || [];
  // Exhaustion is read on the raw bucket count, before the bucket of the
  // events with no space (0) is dropped
  state.datedComplete = buckets.length < poolSize;
  buckets.slice(state.examinedBuckets).forEach(bucket => {
    const spaceId = String(bucket.label);
    if (Number(spaceId) && !state.datedIds.has(spaceId)) {
      state.datedIds.add(spaceId);
      state.pendingIds.push(spaceId);
    }
  });
  state.examinedBuckets = Math.max(state.examinedBuckets, buckets.length);
}

/**
 * Descending, each window is shown as it comes: the engine orders a terms
 * aggregation by a MAX descending accurately. Ascending, the smallest maximum
 * is exact only once every index has returned every space it holds, so the
 * aggregation is read to exhaustion before the first page, and its order is
 * kept for the scroll.
 *
 * @param {object} state the scroll's state, which the read extends
 * @param {number} poolSize the count of buckets asked first
 * @returns {Promise<number>} the count of buckets to ask at the next read
 */
async function readDatedSpaceIds(state, poolSize) {
  do {
    await readDatedWindow(state, poolSize); // eslint-disable-line no-await-in-loop
    poolSize *= 2;
  } while (!state.sortDesc && !state.datedComplete);
  return poolSize;
}

/**
 * Fetches the next pending dated spaces, in the sort's order. A deleted space
 * is absent from the answer: it costs its id, not the page.
 *
 * @param {object} state the scroll's state, whose pending ids are consumed
 * @param {number} count the count of spaces wanted
 * @param {string} expand the space fields to expand
 * @returns {Promise<Array>} the spaces found, in the sort's order
 */
async function fetchDatedSpaces(state, count, expand) {
  const ids = state.pendingIds.splice(0, Math.min(count, MAX_IDS_PER_CALL));
  const data = await Vue.prototype.$spaceService.getSpacesByIds(ids, expand);
  const spacesById = new Map((data?.spaces || []).filter(space => space).map(space => [String(space.id), space]));
  return ids.map(id => spacesById.get(id)).filter(space => space);
}

/**
 * Walks Social's listing of every space by title from the position reached,
 * and keeps the spaces with no collaborative event, until count are found or
 * the listing ends. The position is set after the last space kept, so the
 * next page resumes there.
 *
 * @param {object} state the scroll's state, whose listing position moves
 * @param {number} count the count of spaces wanted
 * @param {string} expand the space fields to expand
 * @param {Set<string>} listedIds the ids already listed, extended with the spaces kept
 * @returns {Promise<Array>} the spaces kept, by title
 */
async function fetchUndatedSpaces(state, count, expand, listedIds) {
  const spaces = [];
  while (spaces.length < count && !state.undatedComplete) {
    const data = await Vue.prototype.$spaceService.getSpacesByFilter({ // eslint-disable-line no-await-in-loop
      filter: 'all',
      expand,
      offset: state.undatedOffset,
      limit: UNDATED_PAGE_SIZE,
      sortBy: 'title',
      sortDirection: 'asc',
    });
    const page = data?.spaces || [];
    let walked = 0;
    for (const space of page) {
      if (spaces.length >= count) {
        break;
      }
      walked++;
      const spaceId = String(space?.id);
      if (space && !state.datedIds.has(spaceId) && !listedIds.has(spaceId)) {
        spaces.push(space);
        listedIds.add(spaceId);
      }
    }
    state.undatedOffset += walked;
    if (walked === page.length && page.length < UNDATED_PAGE_SIZE) {
      state.undatedComplete = true;
    }
  }
  return spaces;
}

/**
 * The Last Activity sort of Manage Spaces, in two phases: the spaces with a
 * collaborative event, by the time of their last one (newest first, or oldest
 * first), then the spaces with none, by title, in both directions. So every
 * space Social lists is listed, and "load more" stops at Social's total.
 * Under this sort the page applies no filter and no keyword.
 *
 * @param {object} options offset (the rows shown, 0 on a refresh), limit,
 *          expand, sortDesc and currentSpaces, the rows shown
 * @returns {Promise<Array>} the rows shown followed by the new page
 */
export async function sortSpacesByLastActivity({
  offset,
  limit,
  expand,
  sortDesc,
  currentSpaces,
}) {
  const shownSpaces = offset ? (currentSpaces || []).slice() : [];
  const state = offset && sortState?.sortDesc === !!sortDesc ? copyState(sortState) : newState(sortDesc);
  const listedIds = new Set(shownSpaces.map(space => String(space.id)));
  const pageSpaces = [];
  let poolSize = Math.max(offset + limit, state.examinedBuckets + limit);
  while (pageSpaces.length < limit && (state.pendingIds.length || !state.datedComplete)) {
    if (state.pendingIds.length) {
      const spaces = await fetchDatedSpaces(state, limit - pageSpaces.length, expand); // eslint-disable-line no-await-in-loop
      spaces.filter(space => !listedIds.has(String(space.id))).forEach(space => {
        pageSpaces.push(space);
        listedIds.add(String(space.id));
      });
    } else {
      poolSize = await readDatedSpaceIds(state, poolSize); // eslint-disable-line no-await-in-loop
    }
  }
  if (pageSpaces.length < limit) {
    pageSpaces.push(...await fetchUndatedSpaces(state, limit - pageSpaces.length, expand, listedIds));
  }
  sortState = state;
  return [...shownSpaces, ...pageSpaces];
}
