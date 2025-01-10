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

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
  type: 'duration',
  options: {
    // Rank of executing 'match' method
    rank: 40,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-duration-sample-item-attribute'],
    match: (fieldName, fieldValue) => (fieldName.toLowerCase().includes('duration') && Number.isInteger(Number(fieldValue)) && Number(fieldValue) > 0),
  },
});

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
  type: 'spaceTemplateId',
  options: {
    rank: 50,
    vueComponent: Vue.options.components['analytics-space-template-sample-item-attribute'],
    match: fieldName => fieldName === 'spaceTemplateId',
  },
});

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
  type: 'categoryId',
  options: {
    rank: 50,
    vueComponent: Vue.options.components['analytics-category-sample-item-attribute'],
    match: fieldName => fieldName === 'categoryId' || fieldName === 'spaceCategoryIds' || fieldName === 'categoryParentId',
  },
});

extensionRegistry.registerExtension('AnalyticsChart', 'FieldValueName', {
  type: 'categoryId',
  match: fieldName => fieldName === 'categoryId' || fieldName === 'categoryParentId',
  getLabel: async (fieldName, fieldValue) => {
    try {
      const translations = await fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/social/translations/category/${fieldValue}/name`, {
        method: 'GET',
        credentials: 'include',
      }).then(resp => resp?.json?.());
      return translations?.[eXo.env.portal.language]
        || translations?.[eXo.env.portal.defaultLanguage]
        || translations?.['en']
        || fieldValue;
    } catch (e) {
      return `${fieldName}=${fieldValue}`;
    }
  },
});
