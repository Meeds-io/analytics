/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
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

extensionRegistry.registerExtension('spaces-administration', 'table-column', {
  rank: 10,
  name: 'last-activity',
  titleKey: 'social.spaces.administration.manageSpaces.lastActivity',
  header: {
    value: 'lastActivityTime',
    align: 'center',
    sortable: true,
    class: 'space-last-activity-header px-1',
    width: '150px',
  },
  componentName: 'spaces-administration-last-activity',
  sortBy: 'lastActivityTime',
  customSort: async ({
    offset,
    limit,
    templateId,
    expand,
    sortDesc,
    currentSpaces,
  }) => {
    const spaces = offset ? currentSpaces?.slice() : [];
    const currentSpacesIds = new Set(spaces.map(s => s.id));
    let poolSize = offset + limit * 3;
    const validSpacesResult = [];
    while (validSpacesResult.length < limit) {  /* eslint-disable no-await-in-loop */
      const aggregationResults = await Vue.prototype.$analyticsService.getChart({
        operations: Vue.prototype.$analyticsService.SPACE_ACTIVITY_OPERATIONS,
        fieldName: templateId && 'spaceTemplateId' || null,
        fieldValues: templateId && [templateId] || null,
        xAggregationField: 'spaceId',
        xAggregationType: 'TERMS',
        yAggregationField: 'timestamp',
        yAggregationType: 'MAX',
        yAggregationSortDirection: sortDesc ? 'desc' : 'asc',
        limit: poolSize
      });
      const spaceIds = aggregationResults?.map(g => g.label).filter(id => Number(id)) || [];

      if (spaceIds.length <= offset) {
        break;
      }

      for (let i = offset; i < spaceIds.length && validSpacesResult.length < limit; i++) {
        const id = spaceIds[i];
        const space = await Vue.prototype.$spaceService.getSpaceById(id, expand, true).catch(() => {/* Space could be deleted */});
        if (space && !currentSpacesIds.has(space.id)) {
          validSpacesResult.push(space);
          currentSpacesIds.add(space.id);
        }
      }
      if (spaceIds.length < poolSize) {
        break;
      }
      poolSize += limit * 2;
    }
    spaces.push(...validSpacesResult);
    return spaces;
  },
});
