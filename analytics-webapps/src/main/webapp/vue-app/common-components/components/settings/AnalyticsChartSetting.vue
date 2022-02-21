<!--
  This file is part of the Meeds project (https://meeds.io/).
  Copyright (C) 2022 Meeds Association
  contact@meeds.io
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
  <exo-drawer
    ref="chartSettingDrawer"
    :drawer-width="drawerWidth"
    allow-expand
    right>
    <template slot="title">
      {{ $t('analytics.settings') }}
    </template>
    <template slot="content">
      <v-card-text>
        <v-tabs
          v-model="tab"
          background-color="transparent"
          color="primary">
          <v-tab>{{ $t('analytics.general') }}</v-tab>
          <v-tab>{{ $t('analytics.colors') }}</v-tab>
          <template v-if="isPercentageBar">
            <v-tab>{{ $t('analytics.percentageValue') }}</v-tab>
            <v-tab>{{ $t('analytics.percentageThreshold') }}</v-tab>
          </template>
          <template v-else>
            <v-tab>{{ $t('analytics.xAxis') }}</v-tab>
            <v-tab>{{ $t('analytics.yAxis') }}</v-tab>
            <v-tab>{{ $t('analytics.multipleCharts') }}</v-tab>
            <v-tab>{{ $t('analytics.dataFilters') }}</v-tab>
          </template>
        </v-tabs>
        <v-tabs-items v-model="tab">
          <v-tab-item eager>
            <analytics-general-setting-form
              ref="settingForm"
              :settings="chartSettings" />
          </v-tab-item>
          <v-tab-item eager>
            <analytics-colors-setting-form
              ref="colorsForm"
              :settings="chartSettings" />
          </v-tab-item>
          <template v-if="isPercentageBar">
            <v-tab-item eager>
              <h3>{{ $t('analytics.computingRule') }}</h3>
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
                ref="searchFilter"
                :fields-mappings="fieldsMappings"
                :settings="chartSettings" />
            </v-tab-item>
            <v-tab-item eager>
              <h3>{{ $t('analytics.computingRule') }}</h3>
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
            </v-tab-item>
            <v-tab-item eager>
              <analytics-y-axis-form
                ref="yAxis"
                :fields-mappings="fieldsMappings"
                :y-axis-aggregation="chartSettings.yAxisAggregation" />
            </v-tab-item>
            <v-tab-item eager>
              <analytics-multiple-charts
                ref="multipleCharts"
                :fields-mappings="fieldsMappings"
                :settings="chartSettings" />
            </v-tab-item>
            <v-tab-item eager>
              <analytics-search-filter-form
                ref="searchFilter"
                :fields-mappings="fieldsMappings"
                :filters="chartSettings.filters"
                no-title />
            </v-tab-item>
          </template>
        </v-tabs-items>
      </v-card-text>
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
    users: {
      type: Object,
      default: function() {
        return null;
      },
    },
    spaces: {
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
      drawerWidth: '650px'
    };
  },
  computed: {
    settingJsonContent() {
      return this.settings && JSON.stringify(this.settings, null, 2);
    },
    chartType() {
      return this.chartSettings && this.chartSettings.chartType;
    },
    isPercentageBar() {
      return this.chartType === 'percentageBar' || this.chartType=== 'percentage';
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
    }
  },
};
</script>
