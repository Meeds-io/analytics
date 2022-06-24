/**
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2022 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
export const USER_TIMEZONE_ID = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;

export function loadUser(users, userId) {
  if (!userId) {
    return Promise.resolve(null);
  }
  userId = parseInt(userId);
  const result = users[userId];
  if (result) {
    return result.catch && result || Promise.resolve(result);
  } else {
    return users[userId] = fetch(`/portal/rest/v1/social/identities/${userId}`)
      .then((resp) => {
        if (resp && resp.ok) {
          return resp.json();
        }
      })
      .then(identity => users[userId] = identity);
  }
}

export function loadSpace(spaces, spaceId) {
  if (!spaceId) {
    return Promise.resolve(null);
  }
  spaceId = parseInt(spaceId);
  const result = spaces[spaceId];
  if (result) {
    return result.catch && result || Promise.resolve(result);
  } else {
    return spaces[spaceId] = fetch(`/portal/rest/v1/social/spaces/${spaceId}`)
      .then((resp) => {
        if (resp && resp.ok) {
          return resp.json();
        }
      })
      .then(space => spaces[spaceId] = space);
  }
}

export function buildPageLinkRecursively(baseUri, nav) {
  if (!nav) {
    return;
  }
  if (nav.uri && nav.pageKey) {
    nav.link = `${baseUri}/${nav.uri}`;
  }
  if (nav.children && nav.children.length) {
    nav.children.forEach(subNav => buildPageLinkRecursively(baseUri, subNav));
  }
}

export function getPage(siteType, siteName, pageName) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/navigations/${siteType}/?siteName=${siteName}&scope=all&visibility=displayed&visibility=system`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error retrieving pages of current site');
    }
    return resp.json();
  }).then(navigations => {
    if (!navigations) {
      return null;
    }
    return getPageRecursively(navigations, pageName);
  });
}

export function toFixed(value, decimals = 2) {
  const fixedNumber = Number.parseFloat(value).toFixed(decimals).replace(/(\..*[1-9])0+$/, '$1').replace(/\.0*$/, '');
  return Number(fixedNumber);
}

function getPageRecursively(navigations, pageName) {
  // Search in first level first
  for (const index in navigations) {
    const navigation = navigations[index];
    if (navigation.name === pageName) {
      return navigation;
    }
  }
  // Search in other levels after
  for (const index in navigations) {
    const navigation = navigations[index];
    if (navigation.children && navigation.children.length) {
      const page = getPageRecursively(navigation.children, pageName);
      if (page) {
        return page;
      }
    }
  }
}