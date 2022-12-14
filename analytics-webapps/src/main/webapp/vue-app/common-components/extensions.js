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
extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
  type: 'userId',
  options: {
    // Rank of executing 'match' method
    rank: 10,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-profile-sample-item-attribute'],
    match: fieldName => (fieldName === 'userId' || fieldName === 'modifierSocialId'),
    options: {
      isUser: true,
    },
  },
});

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
  type: 'spaceId',
  options: {
    // Rank of executing 'match' method
    rank: 10,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-profile-sample-item-attribute'],
    match: fieldName => (fieldName === 'spaceId'),
    options: {
      isSpace: true,
    },
  },
});

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
  type: 'time',
  options: {
    // Rank of executing 'match' method
    rank: 30,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-date-sample-item-attribute'],
    match: (fieldName, fieldValue) => (fieldName.toLowerCase().includes('time') && Number.isInteger(Number(fieldValue)) && Number(fieldValue) > 1600000000000 && Number(fieldValue) < 3000000000000),
  },
});