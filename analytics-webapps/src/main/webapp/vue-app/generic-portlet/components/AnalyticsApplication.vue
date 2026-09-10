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
  <v-app 
    :id="appId"
    class="analytics-application application-body"
    flat>
    <template v-if="canEdit">
      <analytics-chart-setting-drawer
        ref="chartSettingDrawer"
        :retrieve-mappings-url="retrieveMappingsUrl"
        :settings="chartSettings"
        class="mt-0"
        @save="saveSettings" />
      <analytics-json-panel-drawer
        ref="jsonPanelDrawer"
        :settings="chartSettings"
        class="mt-0"
        @save="saveSettings" />
      <analytics-view-samples-drawer
        ref="viewSamplesDrawer"
        :title="$t(title)"
        :selected-period="selectedPeriod"
        :retrieve-samples-url="retrieveChartSamplesUrl"
        class="mt-0" />
    </template>
    <v-card class="ma-auto analytics-chart-parent transparent" flat>
      <div
        ref="chartHeader"
        class="d-flex align-center pa-3 analytics-chart-header"
        flat>
        <v-toolbar-title class="d-flex align-center analytics-chart-title-wrapper">
          <div
            :title="title"
            class="my-auto text-header text-truncate analytics-chart-title">
            {{ title }}
          </div>
        </v-toolbar-title>
        <analytics-period-picker
          :period="selectedPeriod"
          :compact="periodSelectorCompact"
          @change="selectedPeriod = $event" />
        <v-tooltip bottom>
          <template #activator="{ on, attrs }">
            <v-btn
              height="20"
              width="20"
              icon
              small
              color="primary"
              class="my-auto ml-2"
              outlined
              :aria-label="$t('analytics.chartInformation')"
              v-bind="attrs"
              v-on="on">
              <v-icon size="12">fa-info</v-icon>
            </v-btn>
          </template>
          <span>
            <div v-if="title != ''">- {{ title }}</div>
            <div>- {{ $t('analytics.dataRestriction') }}: {{ scopeTooltip }}</div>
            <div v-if="periodTooltipLabel">- {{ periodTooltipLabel }}</div>
          </span>
        </v-tooltip>
        <v-menu
          v-model="showMenu"
          offset-y>
          <template #activator="{ on }">
            <v-btn
              icon
              class="ml-2"
              :aria-label="$t('analytics.moreOptions')"
              v-on="on"
              @blur="closeMenu()">
              <v-icon>mdi-dots-vertical</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item
              v-if="canEdit"
              @mousedown="$event.preventDefault()"
              @click="$refs.viewSamplesDrawer.open()">
              <v-list-item-title>{{ $t('analytics.samples') }}</v-list-item-title>
            </v-list-item>
            <v-list-item
              :href="exportExcelLink"
              :download="exportFileName"
              @mousedown="$event.preventDefault()">
              <v-list-item-title>{{ $t('analytics.export.excel') }}</v-list-item-title>
            </v-list-item>
            <template v-if="canEdit">
              <v-list-item @mousedown="$event.preventDefault()" @click="$refs.chartSettingDrawer.open()">
                <v-list-item-title>{{ $t('analytics.settings.edit.button') }}</v-list-item-title>
              </v-list-item>
              <v-list-item @mousedown="$event.preventDefault()" @click="$refs.jsonPanelDrawer.open()">
                <v-list-item-title>{{ $t('analytics.jsonSettings.edit.button') }}</v-list-item-title>
              </v-list-item>
            </template>
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
    exportExcelUrl: {
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
    periodSelectorCompact: false,
    periodSelectorResizeObserver: null,
    lang: eXo.env.portal.language && eXo.env.portal.language.replace('_', '-'),
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
    periodTooltipLabel() {
      if (!this.selectedPeriod) {
        return null;
      }
      const from = this.formatDate(new Date(this.selectedPeriod.min));
      const to = this.formatDate(new Date(this.selectedPeriod.max));
      return this.$t('analytics.period', [from, to]);
    },
    exportExcelLink() {
      if (!this.exportExcelUrl || !this.selectedPeriod) {
        return null;
      }
      const params = $.param({
        lang: eXo.env.portal.language && eXo.env.portal.language.replace('_','-'),
        min: this.selectedPeriod.min,
        max: this.selectedPeriod.max + 60000,
        timeZone: this.$analyticsUtils.USER_TIMEZONE_ID,
      });
      return `${this.exportExcelUrl}&${params}`;
    },
    exportFileName() {
      const sanitizedTitle = (this.title || 'analytics-chart').replace(/[^a-zA-Z0-9-_]/g, '_');
      const now = new Date();
      const pad = n => `${n}`.padStart(2, '0');
      const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
      return `${sanitizedTitle}_${timestamp}.xlsx`;
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
  mounted() {
    // Watches the header (not the period selector itself, whose own width
    // changes when compact mode toggles - observing it would create a
    // feedback loop that gets stuck compact). Below this width, the date
    // range text can't fit its 25% budget, so only the calendar icon shows.
    this.periodSelectorResizeObserver = new ResizeObserver(entries => {
      this.periodSelectorCompact = entries[0].contentRect.width * 0.25 < 220;
    });
    this.periodSelectorResizeObserver.observe(this.$refs.chartHeader);
    // select-period used to compute and emit this on its own mount; now that
    // it's no longer used, this component owns triggering the first load.
    // The actual configured default (once known) corrects this in getSettings().
    this.initSelectedPeriod();
  },
  beforeDestroy() {
    if (this.periodSelectorResizeObserver) {
      this.periodSelectorResizeObserver.disconnect();
    }
  },
  methods: {
    resolveTitleTranslation(title) {
      if (!title) {
        return '';
      }
      try {
        const translations = JSON.parse(title);
        if (translations && typeof translations === 'object') {
          const lang = eXo?.env?.portal?.language || 'en';
          const defaultLanguage = eXo?.env?.portal?.defaultLanguage || 'en';
          return translations[lang] || translations[defaultLanguage] || Object.values(translations)[0] || '';
        }
        return title;
      } catch (e) {
        // Legacy plain-text title (not yet translated): JSON.parse failed, use as-is
        console.debug('Chart title is not a translations JSON object, using it as plain text', e);
        return title;
      }
    },
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
          this.title = settings && this.resolveTitleTranslation(settings.title) || this.$t('analytics.chartDataPlaceholder');
          // The initial selectedPeriod (set on mount, before settings were
          // known) used a hardcoded guess - correct it once the portlet's
          // actual configured default is known, if it turns out different.
          const configuredDefault = settings && settings.defaultPeriod;
          if (configuredDefault && configuredDefault !== (this.selectedPeriod && this.selectedPeriod.period)) {
            this.initSelectedPeriod(configuredDefault);
          }
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
    },
    formatDate(date) {
      // Explicit locale + options (unlike the shared select-period widget's
      // bare toLocaleDateString() call): safe for display, never fed back
      // into a date input, so it doesn't need to stay ISO-parseable.
      return date.toLocaleDateString(this.lang, {day: 'numeric', month: 'short', year: 'numeric'});
    },
    initSelectedPeriod(periodName) {
      const range = this.$analyticsUtils.computePeriodDateRange(periodName || 'thisMonth');
      this.selectedPeriod = {
        period: periodName || 'thisMonth',
        min: new Date(range.from.getFullYear(), range.from.getMonth(), range.from.getDate()).getTime(),
        max: new Date(range.to.getFullYear(), range.to.getMonth(), range.to.getDate(), 23, 59, 59, 999).getTime(),
      };
    },
  }
};
</script>
