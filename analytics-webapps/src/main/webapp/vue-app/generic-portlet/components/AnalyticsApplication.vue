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
        <div
          class="analytics-chart-period-selector"
          :class="{ 'analytics-chart-period-selector-compact': periodSelectorCompact }">
          <!--
            The shared select-period widget (Meeds-io/social) is not used:
            it fails to mount at all in some states (confirmed via console
            RangeError inside its date-picker header) and separately
            recomputes its calendar from toLocaleDateString() on reopen,
            which the picker can't parse. A self-contained menu avoids both,
            feeding the date-picker plain ISO strings and computing period
            shortcuts directly. It doubles as the full-width display (when
            there is room) and the icon-only compact activator.
          -->
          <v-menu
            v-model="periodSelectorMenu"
            :close-on-content-click="false"
            min-width="420"
            max-width="420"
            offset-y>
            <template #activator="{ on }">
              <span class="d-flex align-center" style="min-width: 0;">
                <v-btn
                  v-if="periodSelectorCompact"
                  ref="periodSelectorActivator"
                  icon
                  :aria-label="$t('analytics.selectPeriod')"
                  v-on="on"
                  @click="initCompactPeriodForm">
                  <v-icon size="18">fa-calendar-alt</v-icon>
                </v-btn>
                <button
                  v-else
                  ref="periodSelectorActivator"
                  type="button"
                  :aria-label="$t('analytics.selectPeriod')"
                  :title="$t('analytics.selectPeriod')"
                  class="analytics-period-selector-full d-flex align-center"
                  v-on="on"
                  @click="initCompactPeriodForm">
                  <v-icon size="16" class="me-2">fa-calendar-alt</v-icon>
                  <span class="text-truncate">{{ periodRangeLabel }}</span>
                </button>
              </span>
            </template>
            <div ref="compactPeriodPopup" class="d-flex flex-column white analytics-compact-period-popup">
              <v-date-picker
                v-model="compactDates"
                :locale="lang"
                :max="compactMaxDate"
                width="100%"
                show-current
                first-day-of-week="1"
                range
                scrollable
                @input="onCompactDatesInput" />
              <v-divider />
              <div class="analytics-compact-period-options">
                <v-btn
                  v-for="item in compactPeriodOptions"
                  :key="item.value"
                  text
                  small
                  @click="selectCompactPeriodItem(item.value)">
                  {{ item.text }}
                </v-btn>
              </div>
              <v-divider />
              <div class="d-flex align-center justify-center px-2 py-3 caption analytics-compact-period-times">
                <span class="mx-1">{{ $t('analytics.from') }}</span>
                <span class="primary--text mx-1">{{ compactFromDateLabel }}</span>
                <input
                  v-model="compactFromTime"
                  type="time"
                  :aria-label="$t('analytics.from')"
                  class="analytics-date-time-selection mx-1"
                  @change="applyCompactPeriod">
                <span class="mx-1">{{ $t('analytics.toDate') }}</span>
                <span class="primary--text mx-1">{{ compactToDateLabel }}</span>
                <input
                  v-model="compactToTime"
                  type="time"
                  :aria-label="$t('analytics.toDate')"
                  class="analytics-date-time-selection mx-1"
                  @change="applyCompactPeriod">
              </div>
            </div>
          </v-menu>
        </div>
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
    periodSelectorMenu: false,
    compactDates: [],
    compactPeriodName: null,
    compactFromTime: '00:00',
    compactToTime: '23:59',
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
    compactPeriodOptions() {
      return [
        {value: 'thisYear', text: this.$t('analytics.periodOptions.thisYear')},
        {value: 'thisSemester', text: this.$t('analytics.periodOptions.thisSemester')},
        {value: 'thisQuarter', text: this.$t('analytics.periodOptions.thisQuarter')},
        {value: 'thisMonth', text: this.$t('analytics.periodOptions.thisMonth')},
        {value: 'thisWeek', text: this.$t('analytics.periodOptions.thisWeek')},
        {value: 'today', text: this.$t('analytics.periodOptions.today')},
      ];
    },
    compactMaxDate() {
      return this.toIsoDate(new Date());
    },
    compactFromDateLabel() {
      return this.compactDates && this.compactDates[0] && this.formatIsoDate(this.compactDates[0]) || '';
    },
    compactToDateLabel() {
      const date = this.compactDates && (this.compactDates[1] || this.compactDates[0]);
      return date && this.formatIsoDate(date) || '';
    },
    periodTooltipLabel() {
      if (!this.selectedPeriod) {
        return null;
      }
      const from = this.formatDate(new Date(this.selectedPeriod.min));
      const to = this.formatDate(new Date(this.selectedPeriod.max));
      return this.$t('analytics.period', [from, to]);
    },
    periodRangeLabel() {
      if (!this.selectedPeriod) {
        return '';
      }
      const from = this.formatDate(new Date(this.selectedPeriod.min));
      const to = this.formatDate(new Date(this.selectedPeriod.max));
      return `${from}~${to}`;
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
    // v-menu's own close-on-click stopped closing the compact period popup
    // in practice (unclear why - possibly a leaked document click listener
    // from an unrelated component), so it's handled explicitly here instead
    // of relying on Vuetify's built-in outside-click detection.
    document.addEventListener('click', this.handlePeriodSelectorOutsideClick, true);
    // select-period used to compute and emit this on its own mount; now that
    // it's no longer used, this component owns triggering the first load.
    // The actual configured default (once known) corrects this in getSettings().
    this.initSelectedPeriod();
  },
  beforeDestroy() {
    if (this.periodSelectorResizeObserver) {
      this.periodSelectorResizeObserver.disconnect();
    }
    document.removeEventListener('click', this.handlePeriodSelectorOutsideClick, true);
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
      } catch (e) {
        // Legacy plain-text title (not yet translated)
      }
      return title;
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
    toIsoDate(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    },
    toIsoTime(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    },
    formatDate(date) {
      // Explicit locale + options (unlike the shared select-period widget's
      // bare toLocaleDateString() call): safe for display, never fed back
      // into a date input, so it doesn't need to stay ISO-parseable.
      return date.toLocaleDateString(this.lang, {day: 'numeric', month: 'short', year: 'numeric'});
    },
    formatIsoDate(isoDateString) {
      return this.formatDate(new Date(`${isoDateString}T00:00:00`));
    },
    initCompactPeriodForm() {
      const from = this.selectedPeriod && new Date(this.selectedPeriod.min);
      const to = this.selectedPeriod && new Date(this.selectedPeriod.max);
      this.compactDates = from && to ? [this.toIsoDate(from), this.toIsoDate(to)] : [];
      this.compactFromTime = from ? this.toIsoTime(from) : '00:00';
      this.compactToTime = to ? this.toIsoTime(to) : '23:59';
      this.compactPeriodName = this.selectedPeriod && this.selectedPeriod.period || null;
    },
    handlePeriodSelectorOutsideClick(event) {
      if (!this.periodSelectorMenu) {
        return;
      }
      const popup = this.$refs.compactPeriodPopup;
      const activator = this.$refs.periodSelectorActivator && this.$refs.periodSelectorActivator.$el;
      if ((popup && popup.contains(event.target)) || (activator && activator.contains(event.target))) {
        return;
      }
      this.applyCompactPeriod();
      this.periodSelectorMenu = false;
    },
    onCompactDatesInput() {
      this.compactPeriodName = null;
      this.compactFromTime = '00:00';
      this.compactToTime = '23:59';
      this.applyCompactPeriod();
    },
    computePeriodDateRange(periodName) {
      const today = new Date();
      let from;
      let to;
      switch (periodName) {
      case 'today':
        from = today;
        to = today;
        break;
      case 'thisWeek': {
        const day = today.getDay();
        const diff = today.getDate() - day + (day === 0 ? -6 : 1);
        from = new Date(new Date().setDate(diff));
        to = new Date(new Date(from).setDate(from.getDate() + 6));
        break;
      }
      case 'thisMonth':
        from = new Date(today.getFullYear(), today.getMonth(), 1);
        to = new Date(today.getFullYear(), today.getMonth() + 1, 0);
        break;
      case 'thisQuarter': {
        const quarter = Math.floor(today.getMonth() / 3);
        from = new Date(today.getFullYear(), quarter * 3, 1);
        to = new Date(today.getFullYear(), quarter * 3 + 3, 0);
        break;
      }
      case 'thisSemester': {
        const semester = Math.floor(today.getMonth() / 6);
        from = new Date(today.getFullYear(), semester * 6, 1);
        to = new Date(today.getFullYear(), semester * 6 + 6, 0);
        break;
      }
      case 'thisYear':
        from = new Date(today.getFullYear(), 0, 1);
        to = new Date(today.getFullYear(), 11, 31);
        break;
      default:
        return null;
      }
      if (to > today) {
        to = today;
      }
      return {from, to};
    },
    selectCompactPeriodItem(periodName) {
      const range = this.computePeriodDateRange(periodName);
      if (!range) {
        return;
      }
      this.compactDates = [this.toIsoDate(range.from), this.toIsoDate(range.to)];
      this.compactFromTime = '00:00';
      this.compactToTime = '23:59';
      this.compactPeriodName = periodName;
      this.applyCompactPeriod();
      this.periodSelectorMenu = false;
    },
    applyCompactPeriod() {
      if (!this.compactDates || !this.compactDates.length) {
        return;
      }
      let [from, to] = this.compactDates;
      if (!to) {
        to = from;
      }
      if (new Date(from) > new Date(to)) {
        [from, to] = [to, from];
      }
      this.selectedPeriod = {
        period: this.compactPeriodName,
        min: new Date(`${from}T${this.compactFromTime || '00:00'}:00`).getTime(),
        max: new Date(`${to}T${this.compactToTime || '23:59'}:59.999`).getTime(),
      };
    },
    initSelectedPeriod(periodName) {
      const range = this.computePeriodDateRange(periodName || 'thisMonth');
      this.selectedPeriod = {
        period: periodName || 'thisMonth',
        min: new Date(range.from.getFullYear(), range.from.getMonth(), range.from.getDate()).getTime(),
        max: new Date(range.to.getFullYear(), range.to.getMonth(), range.to.getDate(), 23, 59, 59, 999).getTime(),
      };
    },
  }
};
</script>
