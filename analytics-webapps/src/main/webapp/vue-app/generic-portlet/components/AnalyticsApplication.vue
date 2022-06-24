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
  <v-app 
    :id="appId"
    class="analytics-application"
    flat>
    <template v-if="canEdit">
      <analytics-chart-setting
        ref="chartSettingDialog"
        :retrieve-mappings-url="retrieveMappingsUrl"
        :settings="chartSettings"
        class="mt-0"
        @save="saveSettings" />
      <analytics-json-panel-dialog
        ref="jsonPanelDialog"
        :settings="chartSettings"
        class="mt-0" />
      <analytics-view-samples-drawer
        ref="viewSamplesDrawer"
        :title="$t(title)"
        :selected-period="selectedPeriod"
        :retrieve-samples-url="retrieveChartSamplesUrl"
        class="mt-0" />
    </template>
    <v-card class="ma-auto analytics-chart-parent white" flat>
      <div class="d-flex pa-3 analytics-chart-header" flat>
        <v-toolbar-title class="d-flex">
          <v-tooltip bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                height="20"
                width="20"
                icon
                small
                class="my-auto me-2 primary"
                outlined
                v-bind="attrs"
                v-on="on">
                <v-icon size="12">fa-info</v-icon>
              </v-btn>
            </template>
            <span>
              <div>- {{ $t('analytics.dataRestriction') }}: {{ scopeTooltip }}</div>
              <div>- {{ $t('analytics.totalSamplesCount') }}: {{ chartsData.dataCount }}</div>
              <div>- {{ $t('analytics.computingTime') }}: {{ chartsData.computingTime }} ms</div>
            </span>
          </v-tooltip>
          <div
            :title="title"
            class="my-auto subtitle-1 text-truncate analytics-chart-title">
            {{ $t(title) }}
          </div>
        </v-toolbar-title>
        <v-spacer />
        <analytics-select-period v-model="selectedPeriod" />
        <v-menu
          v-if="canEdit"
          v-model="showMenu"
          offset-y>
          <template #activator="{ on }">
            <v-btn
              icon
              class="ml-2"
              v-on="on"
              @blur="closeMenu()">
              <v-icon>mdi-dots-vertical</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item @mousedown="$event.preventDefault()" @click="$refs.viewSamplesDrawer.open()">
              <v-list-item-title>{{ $t('analytics.samples') }}</v-list-item-title>
            </v-list-item>
            <v-list-item @mousedown="$event.preventDefault()" @click="$refs.chartSettingDialog.open()">
              <v-list-item-title>{{ $t('analytics.settings') }}</v-list-item-title>
            </v-list-item>
            <v-list-item @mousedown="$event.preventDefault()" @click="$refs.jsonPanelDialog.open()">
              <v-list-item-title>{{ $t('analytics.jsonSettings') }}</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
      </div>

      <v-card-title
        v-if="loading"
        primary-title
        class="ma-auto">
        <v-spacer />
        <v-progress-circular
          color="primary"
          indeterminate
          size="20" />
        <v-spacer />
      </v-card-title>

      <analytics-chart
        ref="analyticsChartBody"
        :title="title"
        :chart-type="chartType"
        :colors="colors" />
    </v-card>
  </v-app>
</template>

<script>
export default {
  props: {
    retrieveSettingsUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    retrieveMappingsUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    retrieveFiltersUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    retrieveChartDataUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    retrieveChartSamplesUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    saveSettingsUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    canEdit: false,
    error: null,
    scope: null,
    title: null,
    chartType: 'line',
    initialized: false,
    showMenu: false,
    displaySamplesCount: false,
    selectedPeriod: null,
    loading: true,
    appId: `AnalyticsApplication${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
    chartsData: {},
    chartSettings: null,
    DEFAULT_COLORS: [
      '#319ab3',
      '#f97575',
      '#98cc81',
      '#4273c8',
      '#cea6ac',
      '#bc99e7',
      '#9ee4f5',
      '#774ea9',
      '#ffa500',
      '#bed67e',
      '#bc99e7',
      '#ffaacc',
    ],
  }),
  computed: {
    scopeTooltip() {
      switch (this.scope) {
      case 'NONE': return this.$t('analytics.permissionDenied');
      case 'GLOBAL': return this.$t('analytics.noDataRestriction');
      case 'USER': return this.$t('analytics.dataRestrictedToCurrentUser');
      case 'SPACE': return this.$t('analytics.dataRestrictedToCurrentSpace');
      }
      return this.error;
    },
    colors() {
      return this.chartSettings
        && this.chartSettings.colors
        && this.chartSettings.colors.length
        && this.chartSettings.colors.slice()
        || this.DEFAULT_COLORS;
    },
  },
  watch: {
    selectedPeriod(newValue, oldValue) {
      if (!oldValue && newValue && !this.initialized) {
        this.initialized = true;
        this.init();
      } else if (newValue) {
        this.updateChart();
      }
    },
  },
  methods: {
    init() {
      this.loading = true;
      return this.getSettings()
        .then(this.$nextTick)
        .then(this.updateChart)
        .then(this.$nextTick)
        .then(this.getFilters)
        .finally(() => {
          this.loading = false;
        });
    },
    getSettings() {
      return fetch(this.retrieveSettingsUrl, {
        method: 'GET',
        credentials: 'include',
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error(`Error getting analytics of chart '${this.title}'`);
          }
        })
        .then((settings) => {
          if (!this.chartSettings) {
            this.chartSettings = settings;
          }
          this.scope = settings && settings.scope;
          this.canEdit = settings && settings.canEdit;
          this.chartType = settings && settings.chartType;
          this.title = settings && settings.title || this.$t('analytics.chartDataPlaceholder');
        })
        .catch((e) => {
          console.error('Error retrieving chart filters', e);
          this.error = 'Error retrieving chart filters';
        });
    },
    getFilters() {
      if (!this.canEdit) {
        return;
      }
      return fetch(this.retrieveFiltersUrl, {
        method: 'GET',
        credentials: 'include',
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error(`Error getting analytics of ${JSON.stringify(this.settings)}`);
          }
        })
        .then((settings) => {
          this.chartSettings = settings;
          if (!settings) {
            this.chartSettings = {
              filters: [],
              aggregations: [],
            };
          }
          if (!this.chartSettings.filters) {
            this.chartSettings.filters = [];
          }
          if (!this.chartSettings.xAxisAggregations) {
            this.chartSettings.xAxisAggregations = [];
          }
          if (!this.chartSettings.yAxisAggregation) {
            this.chartSettings.yAxisAggregation = {};
          }
        })
        .catch((e) => {
          console.error('Error retrieving chart filters', e);
          this.error = 'Error retrieving chart filters';
        });
    },
    saveSettings(chartSettings) {
      this.loading = true;

      return fetch(this.saveSettingsUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: $.param({
          settings: JSON.stringify(chartSettings)
        }),
      })
        .then((resp) => {
          if (!resp || !resp.ok) {
            throw new Error('Error saving chart settings', chartSettings);
          }

          this.chartSettings = JSON.parse(JSON.stringify(chartSettings));
          return this.init();
        })
        .catch((e) => {
          console.error('Error saving chart settings', e);
          this.error = 'Error saving chart settings';
        })
        .finally(() => {
          this.loading = false;
        });
    },
    updateChart() {
      if (!this.selectedPeriod) {
        return;
      }

      this.loading = true;
      const params = {
        lang: eXo.env.portal.language && eXo.env.portal.language.replace('_','-'),
        min: this.selectedPeriod.min,
        max: this.selectedPeriod.max + 60000,
        timeZone: this.$analyticsUtils.USER_TIMEZONE_ID,
      };
      return fetch(this.retrieveChartDataUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: $.param(params),
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error('Error getting analytics with settings:', this.chartSettings);
          }
        })
        .then((chartsData) => {
          this.chartsData = chartsData;
          this.$refs.analyticsChartBody.init(this.chartsData);
        })
        .catch((e) => {
          console.error('fetch analytics - error', e);
          this.error = 'Error getting analytics';
        })
        .finally(() => this.loading = false);
    },
    closeMenu(){
      this.showMenu=false;
    }
  }
};
</script>
