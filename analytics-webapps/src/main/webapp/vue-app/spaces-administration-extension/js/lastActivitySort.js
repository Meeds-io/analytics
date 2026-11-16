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

// The page size of Social's listing, read once into the list of the spaces
// with no collaborative event
const UNDATED_SNAPSHOT_PAGE_SIZE = 100;

// The sort's state for the current scroll: dropped when a refresh starts,
// replaced when a call returns, and continued only by a call whose rows
// shown are the rows the last call returned. So after a failed read the next
// "load more" retries that page, and after a failed refresh, Social's count
// read included, the rows shown are another list's and the next page starts
// a new scroll instead of resuming this one past them
let sortState = null;

function newState(sortDesc) {
  return {
    sortDesc: !!sortDesc,
    // The ids of the rows the last call returned, shown and new, in order
    listedIds: [],
    // The buckets of the aggregation already read, in its order
    examinedBuckets: 0,
    // The aggregation has returned every space it holds
    datedComplete: false,
    // Every dated space id read from the aggregation (D): phase 2 skips them
    datedIds: new Set(),
    // The dated space ids read and not fetched yet, in the sort's order
    pendingIds: [],
    // The ids of the spaces with no collaborative event, by title, not
    // fetched yet: null until Social's listing is read, in one go
    undatedIds: null,
  };
}

function copyState(state) {
  return {
    ...state,
    listedIds: state.listedIds.slice(),
    datedIds: new Set(state.datedIds),
    pendingIds: state.pendingIds.slice(),
    undatedIds: state.undatedIds && state.undatedIds.slice(),
  };
}

/**
 * Tells whether the rows shown are the rows the kept state returned, so the
 * scroll it holds can continue from them.
 *
 * @param {object} state the kept state, or null
 * @param {Array} shownSpaces the rows shown
 * @returns {boolean} true when the state's listed ids are the rows shown, in order
 */
function continuesState(state, shownSpaces) {
  return !!state
    && state.listedIds.length === shownSpaces.length
    && state.listedIds.every((id, index) => id === String(shownSpaces[index].id));
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
  if (!state.sortDesc && !state.datedComplete) {
    // Ascending, a partial window is inexact: nothing is kept from it, the
    // complete answer is taken whole, in its order, once it comes
    return;
  }
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
 * Reads Social's listing of every space by title to its end, in one go, and
 * keeps the ids of the spaces with no collaborative event. Read once per
 * scroll: a page is never asked at an offset of a listing that a bulk action
 * on the rows already listed has shortened meanwhile.
 *
 * @param {object} state the scroll's state, whose dated ids are skipped
 * @returns {Promise<Array<string>>} the ids kept, by title
 */
async function readUndatedSpaceIds(state) {
  const ids = [];
  let offset = 0;
  let page;
  do {
    const data = await Vue.prototype.$spaceService.getSpacesByFilter({ // eslint-disable-line no-await-in-loop
      filter: 'all',
      offset,
      limit: UNDATED_SNAPSHOT_PAGE_SIZE,
      // only the ids are read: with no expand, Social's service asks for the
      // managers of every space, each one a profile built server-side
      expand: 'spaceId',
      sortBy: 'title',
      sortDirection: 'asc',
    });
    page = data?.spaces || [];
    page.forEach(space => {
      const spaceId = String(space?.id);
      if (space && !state.datedIds.has(spaceId)) {
        ids.push(spaceId);
      }
    });
    offset += page.length;
  } while (page.length === UNDATED_SNAPSHOT_PAGE_SIZE);
  return ids;
}

/**
 * Fetches the next spaces with no collaborative event, by title, until count
 * are found or the list ends. A deleted space is absent from the answer: it
 * costs its id, not the page.
 *
 * @param {object} state the scroll's state, whose undated ids are consumed
 * @param {number} count the count of spaces wanted
 * @param {string} expand the space fields to expand
 * @param {Set<string>} listedIds the ids already listed, extended with the spaces kept
 * @returns {Promise<Array>} the spaces kept, by title
 */
async function fetchUndatedSpaces(state, count, expand, listedIds) {
  if (!state.undatedIds) {
    state.undatedIds = await readUndatedSpaceIds(state);
  }
  const spaces = [];
  while (spaces.length < count && state.undatedIds.length) {
    const ids = state.undatedIds.splice(0, Math.min(count - spaces.length, MAX_IDS_PER_CALL));
    const data = await Vue.prototype.$spaceService.getSpacesByIds(ids, expand); // eslint-disable-line no-await-in-loop
    const spacesById = new Map((data?.spaces || []).filter(space => space).map(space => [String(space.id), space]));
    ids.map(id => spacesById.get(id))
      .filter(space => space && !listedIds.has(String(space.id)))
      .forEach(space => {
        spaces.push(space);
        listedIds.add(String(space.id));
      });
  }
  return spaces;
}

/**
 * The Last Activity sort of Manage Spaces, in two phases: the spaces with a
 * collaborative event, by the time of their last one (newest first, or oldest
 * first), then the spaces with none, by title, in both directions. So every
 * space Social lists is listed, and "load more" stops at Social's total.
 * Under this sort the page applies no filter and no keyword. A refresh starts
 * a new scroll before its first read, and a page continues the kept scroll
 * only when the rows shown are the rows it returned: a refresh that failed,
 * in this sort or in Social's count read before it, leaves another list's
 * rows shown, and the next page would otherwise resume the old scroll past
 * them and never list the spaces ranked before.
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
  if (!offset) {
    sortState = null;
  }
  const shownSpaces = offset ? (currentSpaces || []).slice() : [];
  const state = offset && sortState?.sortDesc === !!sortDesc && continuesState(sortState, shownSpaces)
    ? copyState(sortState)
    : newState(sortDesc);
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
  const rows = [...shownSpaces, ...pageSpaces];
  state.listedIds = rows.map(space => String(space.id));
  sortState = state;
  return rows;
}
