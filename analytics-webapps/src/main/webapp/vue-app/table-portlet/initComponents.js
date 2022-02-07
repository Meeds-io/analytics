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