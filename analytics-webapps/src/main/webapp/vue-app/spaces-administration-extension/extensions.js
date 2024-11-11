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
    const spaces = offset ? currentSpaces : [];
    const aggregationResults = await Vue.prototype
      .$analyticsService
      .getChart({
        operations: Vue.prototype.$analyticsService.SPACE_ACTIVITY_OPERATIONS,
        fieldName: templateId && 'spaceTemplateId' || null,
        fieldValues: templateId && [templateId] || null,
        xAggregationField: 'spaceId',
        xAggregationType: 'TERMS',
        xAggregationSortDirection: null,
        yAggregationField: 'timestamp',
        yAggregationType: 'MAX',
        yAggregationSortDirection: sortDesc ? 'desc' : 'asc',
        limit: offset + limit * 2
      });
    const spaceIds = aggregationResults?.map(g => g.label) || [];
    const spacesResult = await Promise.all(
      spaceIds
        .filter(id => Number(id))
        .slice(offset, offset + limit)
        .map(id => Vue.prototype.$spaceService.getSpaceById(id, expand).catch(() => {/* Space could be deleted */}))
    );
    const validSpacesResult = spacesResult.filter(s => s);
    if (validSpacesResult.length < parseInt(limit / 2) && spaceIds.length > (offset + limit)) {
      const additionalSpacesResult = await Promise.all(
        spaceIds
          .filter(id => Number(id))
          .slice(offset + limit, offset + limit * 2)
          .map(id => Vue.prototype.$spaceService.getSpaceById(id, expand).catch(() => {/* Space could be deleted */}))
      );
      additionalSpacesResult.filter(s => s).slice(0, limit - validSpacesResult.length).forEach(s => validSpacesResult.push(s));
    }
    validSpacesResult.forEach(s => spaces.push(s));
    if (validSpacesResult.length === 0) {
      const noActivitySpaces = await Vue.prototype.$spaceService.getSpacesByFilter({
        offset: 0,
        limit: offset + limit,
        templateId,
        expand,
        sortBy: 'title',
        sortDirection: 'asc',
      });
      noActivitySpaces.spaces.filter(s => !spaces.find(s2 => s2.id === s.id)).forEach(s => spaces.push(s));
    }
    return spaces;
  },
});
