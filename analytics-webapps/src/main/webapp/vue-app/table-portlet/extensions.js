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
extensionRegistry.registerExtension('AnalyticsTable', 'CellValue', {
  type: 'user',
  options: {
    // Rank of executing 'match' method
    rank: 40,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-table-cell-user-value'],
    // Method complete signature : match: (fieldName, aggregationType, fieldDataType, item) => { ... }
    match: (fieldName, aggregationType) => fieldName === 'userId' && aggregationType === 'TERMS',
  },
});

extensionRegistry.registerExtension('AnalyticsTable', 'CellValue', {
  type: 'space',
  options: {
    // Rank of executing 'match' method
    rank: 50,
    // Used Vue component to display cell value
    vueComponent: Vue.options.components['analytics-table-cell-space-value'],
    // Method complete signature : match: (fieldName, aggregationType, fieldDataType, item) => { ... }
    match: (fieldName, aggregationType) => fieldName === 'spaceId' && aggregationType === 'TERMS',
  },
});
