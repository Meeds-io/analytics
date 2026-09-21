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
    <div
      ref="tableHeader"
      class="d-flex align-center px-3 pb-2 pt-5 analytics-table-header"
      flat>
      <analytics-period-picker
        :period="selectedPeriod"
        :compact="periodSelectorCompact"
        @change="selectedPeriod = $event" />
      <v-spacer />
      <exo-identity-suggester
        v-if="canUseSuggester"
        v-model="selectedIdentity"
        :search-options="searchOptions"
        :labels="suggesterLabels"
        :include-users="useUsersInSearch"
        :include-spaces="useSpacesInSearch"
        filter-style
        name="selectedUser"
        class="analytics-table-suggester me-2" />
      <v-menu
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
          <v-list-item
            :href="exportExcelLink"
            :download="exportFileName"
            @mousedown="$event.preventDefault()">
            <v-list-item-title>{{ $t('analytics.export.excel') }}</v-list-item-title>
          </v-list-item>
          <template v-if="canEdit">
            <v-list-item @mousedown="$event.preventDefault()" @click="$refs.tableSettingDrawer.open()">
              <v-list-item-title>{{ $t('analytics.settings.edit.button') }}</v-list-item-title>
            </v-list-item>
            <v-list-item @mousedown="$event.preventDefault()" @click="$refs.jsonPanelDrawer.open()">
              <v-list-item-title>{{ $t('analytics.jsonSettings.edit.button') }}</v-list-item-title>
            </v-list-item>
          </template>
        </v-list>
      </v-menu>
    </div>
    <analytics-table
      ref="table"
      :retrieve-table-data-url="retrieveTableDataUrl"
      :settings="settings"
      :period="selectedPeriod"
      :selected-identity="selectedIdentity"
      :user-fields="userFields"
      :space-fields="spaceFields"
      :limit="settings && settings.pageSize"
      :page-size="settings && settings.pageSize" />
    <template v-if="canEdit">
      <analytics-table-setting
        ref="tableSettingDrawer"
        :retrieve-mappings-url="retrieveMappingsUrl"
        :settings="settings"
        :user-fields="userFields"
        :space-fields="spaceFields"
        class="mt-0"
        @save="saveSettings" />
      <analytics-json-panel-drawer
        ref="jsonPanelDrawer"
        :settings="settings"
        class="mt-0"
        @save="saveSettings" />
    </template>
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
    retrieveTableDataUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
    retrieveFieldValuesUrl: {
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
    settings: null,
    appId: `AnalyticsApplication${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
    loading: true,
    canEdit: false,
    selectedIdentity: null,
    error: null,
    scope: null,
    initialized: false,
    showMenu: false,
    selectedPeriod: null,
    periodSelectorCompact: false,
    periodSelectorResizeObserver: null,
    lang: eXo.env.portal.language && eXo.env.portal.language.replace('_', '-'),
    columnsData: {},
    searchOptions: {
      currentUser: '',
      filterType: 'all',
    },
  }),
  computed: {
    suggesterLabels() {
      return {
        searchPlaceholder: this.$t('analytics.searchPlaceholder'),
        placeholder: this.$t('analytics.searchPlaceholder'),
        noDataLabel: this.$t('analytics.noDataLabel'),
      };
    },
    mainColumnAggregation() {
      return this.settings && this.settings.mainColumn && this.settings.mainColumn.valueAggregation && this.settings.mainColumn.valueAggregation.aggregation;
    },
    mainColumnAggregationField() {
      return this.mainColumnAggregation && this.mainColumnAggregation.field;
    },
    useUsersInSearch() {
      return this.mainColumnAggregationField === 'userId';
    },
    useSpacesInSearch() {
      return this.mainColumnAggregationField === 'spaceId';
    },
    canUseSuggester() {
      return this.useUsersInSearch || this.useSpacesInSearch;
    },
    exportExcelLink() {
      if (!this.exportExcelUrl || !this.selectedPeriod) {
        return null;
      }
      const table = this.$refs.table;
      const params = $.param({
        lang: this.lang,
        min: this.selectedPeriod.min,
        max: this.selectedPeriod.max + 60000,
        periodType: this.selectedPeriod.period || '',
        timeZone: this.$analyticsUtils.USER_TIMEZONE_ID,
        sortBy: (table && table.sortBy) || 0,
        sortDirection: (table && table.sortDirection) || 'desc',
      });
      return `${this.exportExcelUrl}&${params}`;
    },
    exportFileName() {
      const sanitizedTitle = (this.title || 'analytics-table').replace(/[^a-zA-Z0-9-_]/g, '_');
      const now = new Date();
      const pad = n => `${n}`.padStart(2, '0');
      const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
      return `${sanitizedTitle}_${timestamp}.xlsx`;
    },
    scopeTooltip() {
      switch (this.scope) {
      case 'NONE': return this.$t('analytics.permissionDenied');
      case 'GLOBAL': return this.$t('analytics.noDataRestriction');
      case 'USER': return this.$t('analytics.dataRestrictedToCurrentUser');
      case 'SPACE': return this.$t('analytics.dataRestrictedToCurrentSpace');
      }
      return this.error;
    },
    userFields() {
      return [{
        name: 'createdDate',
        label: this.$t('analytics.createdDate'),
        sortable: true,
        type: 'date',
      }];
    },
    spaceFields() {
      return [{
        name: 'createdTime',
        label: this.$t('analytics.createdDate'),
        sortable: true,
        type: 'date',
      }, {
        name: 'managersCount',
        label: this.$t('analytics.managersCount'),
        sortable: true,
        type: 'long',
      }, {
        name: 'membersCount',
        label: this.$t('analytics.membersCount'),
        sortable: true,
        type: 'long',
      }, {
        name: 'redactorsCount',
        label: this.$t('analytics.redactorsCount'),
        sortable: true,
        type: 'long',
      }, {
        name: 'template',
        label: this.$t('analytics.template'),
        sortable: true,
        type: 'string',
      }, {
        name: 'subscription',
        label: this.$t('analytics.subscription'),
        sortable: false,
        type: 'string',
      }, {
        name: 'visibility',
        label: this.$t('analytics.visibility'),
        sortable: false,
        type: 'string',
      }];
    },
  },
  watch: {
    selectedPeriod(newValue, oldValue) {
      if (!oldValue && newValue && !this.initialized) {
        this.initialized = true;
        this.init();
      }
    },
  },
  created() {
    this.$root.retrieveSettingsUrl = this.retrieveSettingsUrl;
    this.$root.retrieveMappingsUrl = this.retrieveMappingsUrl;
    this.$root.retrieveFiltersUrl = this.retrieveFiltersUrl;
    this.$root.retrieveTableDataUrl = this.retrieveTableDataUrl;
    this.$root.retrieveFieldValuesUrl = this.retrieveFieldValuesUrl;
    this.$root.saveSettingsUrl = this.saveSettingsUrl;
  },
  mounted() {
    this.periodSelectorResizeObserver = new ResizeObserver(entries => {
      this.periodSelectorCompact = entries[0].contentRect.width * 0.25 < 220;
    });
    this.periodSelectorResizeObserver.observe(this.$refs.tableHeader);
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
        console.debug('Table title is not a translations JSON object, using it as plain text', e);
        return title;
      }
    },
    init() {
      this.loading = true;
      return this.getSettings()
        .then(this.$nextTick)
        .then(this.getFilters)
        .then(this.$nextTick)
        .then(this.updateTable)
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
            throw new Error(`Error getting analytics of table '${this.title}'`);
          }
        })
        .then((settings) => {
          this.scope = settings && settings.scope;
          this.canEdit = settings && settings.canEdit;
          this.title = settings && this.resolveTitleTranslation(settings.title) || '';
          const configuredDefault = settings && settings.defaultPeriod;
          if (configuredDefault && configuredDefault !== (this.selectedPeriod && this.selectedPeriod.period)) {
            this.initSelectedPeriod(configuredDefault);
          }
        })
        .catch((e) => {
          console.error('Error retrieving table settings', e);
          this.error = 'Error retrieving table settings';
        });
    },
    getFilters() {
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
          if (!settings) {
            settings = {};
          }
          if (!settings.mainColumn) {
            settings.mainColumn = {
              title: settings?.mainColumn?.title || '',
              valueAggregation: {
                
              },
              thresholdAggregation: {
                
              },
              aggregation: {
                field: settings?.mainColumn?.field || null,
                type: settings?.mainColumn?.type || 'TERMS',
              },
            };
          }
          if (!settings.columns) {
            settings.columns = [];
          }
          this.settings = settings;
        })
        .catch((e) => {
          console.error('Error retrieving table filters', e);
          this.error = 'Error retrieving table filters';
        });
    },
    saveSettings(settings) {
      this.loading = true;

      const settingsToSave = JSON.parse(JSON.stringify(settings));

      // Cleanup saved settings
      settingsToSave.columns.forEach(column => {
        if (!column.valueAggregation || !column.valueAggregation.aggregation || !column.valueAggregation.aggregation.type) {
          delete column.valueAggregation;
        }
        if (!column.thresholdAggregation || !column.thresholdAggregation.aggregation || !column.thresholdAggregation.aggregation.type) {
          delete column.thresholdAggregation;
        }
      });
      if (!settingsToSave.mainColumn.valueAggregation || !settingsToSave.mainColumn.valueAggregation.aggregation || !settingsToSave.mainColumn.valueAggregation.aggregation.type) {
        delete settingsToSave.mainColumn.valueAggregation;
      }

      return fetch(this.saveSettingsUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: $.param({
          settings: JSON.stringify(settingsToSave)
        }),
      })
        .then((resp) => {
          if (!resp || !resp.ok) {
            throw new Error('Error saving table settings', settingsToSave);
          }

          return this.init();
        })
        .catch((e) => {
          console.error('Error saving table settings', e);
          this.error = 'Error saving table settings';
        })
        .finally(() => {
          this.loading = false;
        });
    },
    updateTable() {
      if (!this.selectedPeriod) {
        return;
      }
      this.$refs.table.refresh();
    },
    closeMenu(){
      this.showMenu=false;
    },
    initSelectedPeriod(periodName) {
      const name = periodName || 'thisMonth';
      const range = this.$analyticsUtils.computePeriodDateRangeOrDefault(name);
      this.selectedPeriod = {
        // an unknown name would describe a period that does not exist while
        // the range above already fell back to thisMonth: keep them in sync.
        period: this.$analyticsUtils.computePeriodDateRange(name) ? name : 'thisMonth',
        min: new Date(range.from.getFullYear(), range.from.getMonth(), range.from.getDate()).getTime(),
        max: new Date(range.to.getFullYear(), range.to.getMonth(), range.to.getDate(), 23, 59, 59, 999).getTime(),
      };
    },
  }
};
</script>