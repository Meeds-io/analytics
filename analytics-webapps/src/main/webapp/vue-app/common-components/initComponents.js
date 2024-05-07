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
import AnalyticsChartSetting from './components/settings/AnalyticsChartSetting.vue';
import JsonPanelDialog from './components/settings/JsonPanelDialog.vue';

import IdentityFieldSelection from './components/settings/form/IdentityFieldSelection.vue';
import FieldSelection from './components/settings/form/FieldSelection.vue';
import FieldFilter from './components/settings/form/FieldFilter.vue';
import SpaceFieldFilter from './components/settings/form/SpaceFieldFilter.vue';
import UserFieldFilter from './components/settings/form/UserFieldFilter.vue';
import TextValuesFilter from './components/settings/form/TextValuesFilter.vue';
import TextValueSuggester from './components/settings/form/TextValueSuggester.vue';
import MultipleValuesSelection from './components/settings/form/MultipleValuesSelection.vue';
import XAxisAggregationField from './components/settings/form/XAxisAggregationField.vue';
import LimitFilterForm from './components/settings/form/LimitFilterForm.vue';

import SettingColorPicker from './components/settings/tabs/SettingColorPicker.vue';
import GeneralSettingForm from './components/settings/tabs/GeneralSettingForm.vue';
import ColorsSettingForm from './components/settings/tabs/ColorsSettingForm.vue';
import SearchFilterForm from './components/settings/tabs/SearchFilterForm.vue';
import XAxisForm from './components/settings/tabs/XAxisAggregationForm.vue';
import YAxisForm from './components/settings/tabs/YAxisAggregationForm.vue';
import MultipleCharts from './components/settings/tabs/MultipleChartsAggregationForm.vue';

import ViewSamplesDrawer from './components/samples/ViewSamplesDrawer.vue';
import ProfileChip from './components/samples/ProfileChip.vue';
import SampleItem from './components/samples/SampleItem.vue';
import SampleItemAttribute from './components/samples/SampleItemAttribute.vue';
import ProfileSampleItemAttribute from './components/samples/ProfileSampleItemAttribute.vue';
import DateSampleItemAttribute from './components/samples/DateSampleItemAttribute.vue';
import DurationSampleItemAttribute from './components/samples/DurationSampleItemAttribute.vue';

import SelectPeriod from './components/common/SelectPeriod.vue';

const components = {
  'analytics-profile-chip': ProfileChip,
  'analytics-sample-item': SampleItem,
  'analytics-sample-item-attribute': SampleItemAttribute,
  'analytics-profile-sample-item-attribute': ProfileSampleItemAttribute,
  'analytics-date-sample-item-attribute': DateSampleItemAttribute,
  'analytics-duration-sample-item-attribute': DurationSampleItemAttribute,
  'analytics-setting-color-picker': SettingColorPicker,
  'analytics-identity-field-selection': IdentityFieldSelection,
  'analytics-field-selection': FieldSelection,
  'analytics-multiple-values-selection': MultipleValuesSelection,
  'analytics-space-field-filter': SpaceFieldFilter,
  'analytics-user-field-filter': UserFieldFilter,
  'analytics-text-value-filter': TextValuesFilter,
  'analytics-text-value-suggester': TextValueSuggester,
  'analytics-field-filter': FieldFilter,
  'analytics-x-axis-aggregation-field': XAxisAggregationField,
  'analytics-limit-filter-form': LimitFilterForm,
  'analytics-general-setting-form': GeneralSettingForm,
  'analytics-colors-setting-form': ColorsSettingForm,
  'analytics-search-filter-form': SearchFilterForm,
  'analytics-x-axis-form': XAxisForm,
  'analytics-y-axis-form': YAxisForm,
  'analytics-multiple-charts': MultipleCharts,
  'analytics-select-period': SelectPeriod,
  'analytics-chart-setting': AnalyticsChartSetting,
  'analytics-json-panel-dialog': JsonPanelDialog,
  'analytics-view-samples-drawer': ViewSamplesDrawer,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

import * as analyticsUtils from './js/utils.js';

if (!Vue.prototype.$analyticsUtils) {
  window.Object.defineProperty(Vue.prototype, '$analyticsUtils', {
    value: analyticsUtils,
  });
}
