/**
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
export const USER_TIMEZONE_ID = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;

/**
 * A GROUP_BY aggregation threshold is a minimum number of occurrences: only a whole
 * number greater than or equal to 1 is meaningful, and the backend field it feeds
 * (AnalyticsAggregation.minDocCount) is a long.
 *
 * @param {Number|String} threshold the threshold to check, as typed in the settings form
 * @returns {Boolean} true when the threshold is a whole number greater than or equal to 1
 */
export function isValidThreshold(threshold) {
  const value = Number(threshold);
  return Number.isInteger(value) && value >= 1;
}

/**
 * Resolves one of the period shortcuts offered by the period picker into the
 * date range it covers, never running past today (analytics has no data in
 * the future, and an end bound beyond today makes the chart draw an empty
 * tail).
 *
 * @param {String} periodName one of today, thisWeek, thisMonth, thisQuarter,
 *          thisSemester, thisYear
 * @returns {Object} {from, to} Dates, or null for an unknown period name
 */
export function computePeriodDateRange(periodName) {
  const today = new Date();
  let from;
  let to;
  switch (periodName) {
  case 'today':
    from = today;
    to = today;
    break;
  case 'thisWeek': {
    const day = today.getDay();
    const diff = today.getDate() - day + (day === 0 ? -6 : 1);
    from = new Date(new Date().setDate(diff));
    to = new Date(new Date(from).setDate(from.getDate() + 6));
    break;
  }
  case 'thisMonth':
    from = new Date(today.getFullYear(), today.getMonth(), 1);
    to = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    break;
  case 'thisQuarter': {
    const quarter = Math.floor(today.getMonth() / 3);
    from = new Date(today.getFullYear(), quarter * 3, 1);
    to = new Date(today.getFullYear(), quarter * 3 + 3, 0);
    break;
  }
  case 'thisSemester': {
    const semester = Math.floor(today.getMonth() / 6);
    from = new Date(today.getFullYear(), semester * 6, 1);
    to = new Date(today.getFullYear(), semester * 6 + 6, 0);
    break;
  }
  case 'thisYear':
    from = new Date(today.getFullYear(), 0, 1);
    to = new Date(today.getFullYear(), 11, 31);
    break;
  default:
    return null;
  }
  if (to > today) {
    to = today;
  }
  return {from, to};
}

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

export async function getProfilePropertyLabel(objectId, language) {
  try {
    const response = await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/profile/label/profileProperty/${objectId}/${language}`, {
      method: 'GET',
      credentials: 'include',
    });
    if (!response?.ok) {
      return null;
    }
    const text = await response.text();
    if (!text) {
      return null;
    }
    const label = JSON.parse(text);
    return label?.label || null;
  } catch (error) {
    return null;
  }
}

export async function getProfilePropertySetting(settingName) {
  try {
    const response = await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/profile/settings/${settingName}`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      }
    });
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch (error) {
    return null;
  }
}

export async function getPropertyOptionTranslatedValue(optionId, lang) {
  return await getTranslations('propertySettingOption', optionId, 'optionValue').then(translations => {
    return translations[lang] || translations[eXo.env.portal.defaultLanguage];
  });
}

async function getTranslations(objectType, objectId, fieldName) {
  return await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/social/translations/${objectType}/${objectId}/${fieldName}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp?.ok) {
      return resp.json();
    } else {
      throw new Error('Error when getting list of translations of dropdown option value');
    }
  });
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
