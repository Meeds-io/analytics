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
export const SPACE_ACTIVITY_OPERATIONS = [
  'spaceCreated',
  'spaceRemoved',
  'spaceRenamed',
  'spaceDescriptionEdited',
  'spaceAccessEdited',
  'spaceRegistrationEdited',
  'spaceAvatarEdited',
  'spaceBannerEdited',
  'joined',
  'left',
  'pinActivity',
  'createActivity',
  'createComment',
  'likeActivity',
  'likeComment',
  'sendKudos',
  'taskCreated',
  'taskUpdated',
  'taskCommented',
  'taskTitleChanged',
  'taskDescriptionChanged',
  'taskStatusChanged',
  'taskCompleted',
  'noteCreated',
  'noteUpdated',
  'createRealization',
  'createRule',
  'exo.news.postArticle',
];

export function getSamples(options) {
  const formData = new FormData();
  if (options.operations?.length) {
    options.operations.forEach(o => formData.append('operation', o));
  }
  if (options.fieldName) {
    formData.append('fieldName', options.fieldName);
  }
  if (options.fieldValues?.length) {
    options.fieldValues.forEach(v => formData.append('fieldValue', v));
  }
  formData.append('sortBy', options.sortBy);
  formData.append('sortDirection', options.sortDirection);
  formData.append('limit', options.limit || 25);
  const params = decodeURIComponent(new URLSearchParams(formData).toString());

  return fetch(`/analytics/rest/samples?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getChart(options) {
  const formData = new FormData();
  if (options.operations?.length) {
    options.operations.forEach(o => formData.append('operation', o));
  }
  if (options.fieldName) {
    formData.append('fieldName', options.fieldName);
  }
  if (options.fieldValues?.length) {
    options.fieldValues.forEach(v => formData.append('fieldValue', v));
  }

  formData.append('xAggregationField', options.xAggregationField);
  formData.append('xAggregationType', options.xAggregationType?.toUpperCase());
  if (options.xAggregationSortDirection) {
    formData.append('xAggregationSortDirection', options.xAggregationSortDirection);
  }

  formData.append('yAggregationField', options.yAggregationField);
  formData.append('yAggregationType', options.yAggregationType?.toUpperCase());
  if (options.yAggregationSortDirection) {
    formData.append('yAggregationSortDirection', options.yAggregationSortDirection);
  }
  formData.append('limit', options.limit || 25);
  const params = decodeURIComponent(new URLSearchParams(formData).toString());
  return fetch(`/analytics/rest/chart?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
