<!--
  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 3 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <div>
  <exo-drawer
    ref="chartSettingDrawer"
    :drawer-width="drawerWidth"
    allow-expand
    right
    @expand-updated="drawerExpanded = $event">
    <template slot="title">
      {{ $t('analytics.settings.drawer.title') }}
    </template>
    <template slot="content">
      <v-form class="pa-4 analyticsChartSettingDrawer" @submit="$event.preventDefault(); $event.stopPropagation();">
        <v-tabs
          v-model="tab"
          background-color="transparent"
          color="primary"
          class="mb-4">
          <v-tab>{{ $t('analytics.general') }}</v-tab>
          <template v-if="isPercentageBar">
            <v-tab>{{ $t('analytics.percentageValue') }}</v-tab>
            <v-tab>{{ $t('analytics.percentageThreshold') }}</v-tab>
          </template>
          <template v-else>
            <v-tab>{{ $t('analytics.xAxis') }}</v-tab>
            <v-tab>{{ $t('analytics.yAxis') }}</v-tab>
            <v-tab>{{ $t('analytics.dataFilters') }}</v-tab>
          </template>
        </v-tabs>
        <v-tabs-items v-model="tab">
          <v-tab-item eager>
            <label class="text-header me-1 my-4" :for="`analyticsChartTitleInput${uid}`">{{ $t('analytics.chartHeader') }}</label>
            <div class="width-auto flex-grow-1 mt-1 mb-4">
              <v-text-field
                :id="`analyticsChartTitleInput${uid}`"
                v-model="chartSettings.title"
                outlined
                dense
                hide-details
                required />
            </div>
            <label class="text-header me-1 my-4" :for="`analyticsChartDefaultPeriodInput${uid}`">{{ $t('analytics.defaultPeriod') }}</label>
            <div class="width-auto flex-grow-1 mt-1 mb-4">
              <v-select
                :id="`analyticsChartDefaultPeriodInput${uid}`"
                v-model="chartSettings.defaultPeriod"
                :items="defaultPeriodOptions"
                :value-comparator="selectedValueComparator"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                chips />
            </div>
            <label class="text-header me-1 my-4" :for="`analyticsChartTypeInput${uid}`">{{ $t('analytics.chartType') }}</label>
            <div class="width-auto flex-grow-1 mt-1 mb-4">
              <v-select
                :id="`analyticsChartTypeInput${uid}`"
                v-model="chartSettings.chartType"
                :items="chartTypes"
                :value-comparator="selectedValueComparator"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                chips />
            </div>
            <v-list-item class="px-0 mb-1 d-flex align-center">
              <v-list-item-content>
                <v-list-item-title class="text-left">{{ $t('analytics.colors') }}</v-list-item-title>
              </v-list-item-content>
              <analytics-colors-setting-form
                v-if="!isMultipleColors"
                ref="colorsForm"
                :settings="chartSettings"
                :show-titles="false"
                class="ms-auto" />
              <v-btn
                v-else
                icon
                class="ms-auto"
                :aria-label="$t('analytics.colors')"
                @click="$refs.colorsDrawer.open()">
                <v-icon class="icon-default-color" size="16">fa-caret-right</v-icon>
              </v-btn>
            </v-list-item>
          </v-tab-item>
          <template v-if="isPercentageBar">
            <v-tab-item eager>
              <div class="text-header mb-4">{{ $t('analytics.computingRule') }}</div>
              <analytics-y-axis-form
                ref="yAxis"
                :fields-mappings="fieldsMappings"
                :y-axis-aggregation="chartSettings.value.yAxisAggregation" />
              <v-divider class="my-4" />
              <analytics-search-filter-form
                ref="searchFilter"
                :fields-mappings="fieldsMappings"
                :filters="chartSettings.value.filters" />
              <analytics-limit-filter-form
                ref="limitFilter"
                :fields-mappings="fieldsMappings"
                :settings="chartSettings" />
            </v-tab-item>
            <v-tab-item eager>
              <div class="text-header mb-4">{{ $t('analytics.computingRule') }}</div>
              <analytics-y-axis-form
                ref="yAxis"
                :fields-mappings="fieldsMappings"
                :y-axis-aggregation="chartSettings.threshold.yAxisAggregation" />
              <v-divider class="my-4" />
              <analytics-search-filter-form
                ref="searchFilter"
                :fields-mappings="fieldsMappings"
                :filters="chartSettings.threshold.filters" />
            </v-tab-item>
          </template>
          <template v-else>
            <v-tab-item eager>
              <analytics-x-axis-form
                ref="xAxis"
                :fields-mappings="fieldsMappings"
                :settings="chartSettings" />
              <v-divider class="my-4" />
              <analytics-multiple-charts
                ref="multipleCharts"
                :fields-mappings="fieldsMappings"
                :settings="chartSettings" />
            </v-tab-item>
            <v-tab-item eager>
              <analytics-y-axis-form
                ref="yAxis"
                :fields-mappings="fieldsMappings"
                :y-axis-aggregation="chartSettings.yAxisAggregation" />
            </v-tab-item>
            <v-tab-item eager>
              <v-switch
                v-model="chartSettings.spaceOnly"
                :label="$t('analytics.spaceOnly')"
                class="my-auto text-no-wrap" />
              <v-divider class="my-4" />
              <analytics-search-filter-form
                ref="searchFilter"
                :fields-mappings="fieldsMappings"
                :filters="chartSettings.filters"
                :expand="drawerExpanded"
                no-title />
            </v-tab-item>
          </template>
        </v-tabs-items>
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <button class="btn ignore-vuetify-classes me-1" @click="close">
          {{ $t('analytics.close') }}
        </button>
        <button class="btn btn-primary ignore-vuetify-classes ms-1" @click="save">
          {{ $t('analytics.save') }}
        </button>
      </div>
    </template>
  </exo-drawer>
  <exo-drawer
    v-if="isMultipleColors"
    ref="colorsDrawer"
    drawer-width="420px"
    right>
    <template slot="title">
      {{ $t('analytics.colors') }}
    </template>
    <template slot="content">
      <v-form class="pa-4">
        <analytics-colors-setting-form
          ref="colorsFormMulti"
          :settings="chartSettings" />
      </v-form>
    </template>
  </exo-drawer>
  </div>
</template>

<script>

export default {
  props: {
    retrieveMappingsUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    settings: {
      type: Object,
      default: function() {
        return null;
      },
    },
  },
  data() {
    return {
      chartSettings: {},
      fieldsMappings: [],
      dialog: false,
      tab: 0,
      drawerWidth: '420px',
      drawerExpanded: false
    };
  },
  computed: {
    uid() {
      return this._uid;
    },
    settingJsonContent() {
      return this.settings && JSON.stringify(this.settings, null, 2);
    },
    chartType() {
      return this.chartSettings && this.chartSettings.chartType;
    },
    isPercentageBar() {
      return this.chartType === 'percentageBar' || this.chartType=== 'percentage';
    },
    isMultipleColors() {
      return this.chartSettings && (this.chartSettings.multipleChartsField
        || this.chartSettings.chartType === 'pie'
        || this.chartSettings.chartType === 'doughnut'
        || this.chartSettings.chartType === 'nightingale'
        || this.chartSettings.chartType === 'stackedBar');
    },
    chartTypes(){
      if (this.isPercentageBar){
        return [
          {
            text: 'Percentage Bar',
            value: 'percentageBar',
          },
          {
            text: 'Percentage',
            value: 'percentage',
          }
        ];
      } else {
        return [
          {
            text: this.$t('analytics.bar'),
            value: 'bar',
          },
          {
            text: this.$t('analytics.line'),
            value: 'line',
          },
          {
            text: this.$t('analytics.pie'),
            value: 'pie',
          },
          {
            text: this.$t('analytics.stackedBar'),
            value: 'stackedBar',
          },
          {
            text: this.$t('analytics.doughnut'),
            value: 'doughnut',
          },
          {
            text: this.$t('analytics.nightingale'),
            value: 'nightingale',
          },
        ];
      }
    },
    defaultPeriodOptions() {
      return [
        {value: 'today', text: this.$t('analytics.periodOptions.today')},
        {value: 'thisWeek', text: this.$t('analytics.periodOptions.thisWeek')},
        {value: 'thisMonth', text: this.$t('analytics.periodOptions.thisMonth')},
        {value: 'thisQuarter', text: this.$t('analytics.periodOptions.thisQuarter')},
        {value: 'thisSemester', text: this.$t('analytics.periodOptions.thisSemester')},
        {value: 'thisYear', text: this.$t('analytics.periodOptions.thisYear')},
      ];
    },
  },
  watch: {
    dialog() {
      if (this.dialog) {
        this.init();
      }
    },
    isPercentageBar() {
      if (this.isPercentageBar) {
        this.chartSettings.multipleChartsField = null;
      } else {
        this.chartSettings.multipleChartsField = this.settings.multipleChartsField;
      }
    },
  },
  methods: {
    init() {
      this.loading = true;

      return fetch(this.retrieveMappingsUrl, {
        method: 'GET',
        credentials: 'include',
        headers: {
          Accept: 'application/json',
        },
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error('Error getting analytics fields mappings:');
          }
        })
        .then((fieldsMappings) => {
          this.fieldsMappings = fieldsMappings;
          return this.$nextTick();
        })
        .then(() => {
          if (this.$refs) {
            Object.keys(this.$refs).forEach(refKey => {
              const component = this.$refs[refKey];
              if (component && component.init) {
                component.init();
              }
            });
          }
        })
        .catch((e) => {
          console.error('fetch analytics - error', e);
          this.error = 'Error getting analytics';
        })
        .finally(() => this.loading = false);
    },
    open() {
      this.chartSettings = JSON.parse(JSON.stringify(this.settings));
      if (!this.chartSettings.defaultPeriod) {
        this.chartSettings.defaultPeriod = 'thisMonth';
      }
      this.tab = 0;
      this.drawerExpanded = false;
      this.dialog = true;
      this.$refs.chartSettingDrawer.open();
    },
    save() {
      this.$emit('save', this.chartSettings);
      this.dialog = false;
      this.$refs.chartSettingDrawer.close();
    },
    close() {
      this.dialog = false;
      this.$refs.chartSettingDrawer.close();
    },
    selectedValueComparator(item1, item2){
      const item1Value = (item1 && item1.value) || item1;
      const item2Value = (item2 && item2.value) || item2;
      return item1Value === item2Value;
    },
  },
};
</script>
