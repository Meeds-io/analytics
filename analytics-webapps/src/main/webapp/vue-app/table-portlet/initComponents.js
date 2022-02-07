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
import AnalyticsTableApplication from './components/AnalyticsTableApplication.vue';
import AnalyticsTableSetting from './components/settings/AnalyticsTableSetting.vue';
import AnalyticsTableColumnSetting from './components/settings/AnalyticsTableColumnSetting.vue';
import AnalyticsTableColumnAggregationSetting from './components/settings/AnalyticsTableColumnAggregationSetting.vue';
import AnalyticsTableGeneralSetting from './components/settings/AnalyticsTableGeneralSetting.vue';
import AnalyticsTable from './components/table/AnalyticsTable.vue';
import AnalyticsTableCell from './components/table/AnalyticsTableCell.vue';
import AnalyticsTableCellValue from './components/table/AnalyticsTableCellValue.vue';
import AnalyticsTableCellUserValue from './components/table/AnalyticsTableCellUserValue.vue';
import AnalyticsTableCellSpaceValue from './components/table/AnalyticsTableCellSpaceValue.vue';

const components = {
  'analytics-table-application': AnalyticsTableApplication,
  'analytics-table-column-aggregation-setting': AnalyticsTableColumnAggregationSetting,
  'analytics-table-column-setting': AnalyticsTableColumnSetting,
  'analytics-table-general-setting': AnalyticsTableGeneralSetting,
  'analytics-table-setting': AnalyticsTableSetting,
  'analytics-table': AnalyticsTable,
  'analytics-table-cell': AnalyticsTableCell,
  'analytics-table-cell-value': AnalyticsTableCellValue,
  'analytics-table-cell-user-value': AnalyticsTableCellUserValue,
  'analytics-table-cell-space-value': AnalyticsTableCellSpaceValue,
};

for (const key in components) {
  Vue.component(key, components[key]);
}