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
  <exo-drawer
    ref="samplesDrawer"
    body-classes="hide-scroll"
    class="samplesDrawer"
    right
    allow-expand
    @closed="closed">
    <template slot="title">
      {{ title }}
    </template>
    <template slot="titleIcons">
      <v-btn
        :disabled="loading"
        icon
        color="primary"
        class="mx-2"
        @click="refresh">
        <v-icon>
          refresh
        </v-icon>
      </v-btn>
    </template>
    <template slot="content">
      <v-row
        v-if="drawerOpened"
        justify="center"
        class="ma-0 px-2 pt-2 flex-wrap analyticsSamplesDateFilter">
        <v-text-field
          v-model="fromDate"
          :label="$t('analytics.from')"
          :max="toDate"
          type="date"
          dense
          outlined
          hide-details
          class="mx-1 mb-2 flex-grow-0"
          style="max-width: 170px;"
          @change="applyDateFilter" />
        <v-text-field
          v-model="fromTime"
          type="time"
          dense
          outlined
          hide-details
          class="mx-1 mb-2 flex-grow-0"
          style="max-width: 120px;"
          @change="applyDateFilter" />
        <v-text-field
          v-model="toDate"
          :label="$t('analytics.toDate')"
          :min="fromDate"
          type="date"
          dense
          outlined
          hide-details
          class="mx-1 mb-2 flex-grow-0"
          style="max-width: 170px;"
          @change="applyDateFilter" />
        <v-text-field
          v-model="toTime"
          type="time"
          dense
          outlined
          hide-details
          class="mx-1 mb-2 flex-grow-0"
          style="max-width: 120px;"
          @change="applyDateFilter" />
      </v-row>
      <v-row justify="center" class="ma-0 analyticsDrawerContent">
        <v-expansion-panels v-if="chartDatas" accordion>
          <analytics-sample-item
            v-for="chartData in chartDatas"
            :key="chartData.timestamp"
            :chart-data="chartData"
            :sample-item-extensions="sampleItemExtensions" />
        </v-expansion-panels>
      </v-row>
    </template>
    <template v-if="canLoadMore" slot="footer">
      <div class="d-flex">
        <v-btn
          :disabled="loading"
          color="primary"
          class="ma-auto"
          text
          @click="loadMore">
          Load More
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  emits: ['cancel'],
  props: {
    selectedPeriod: {
      type: Object,
      default: function() {
        return null;
      },
    },
    title: {
      type: String,
      default: function() {
        return null;
      },
    },
    retrieveSamplesUrl: {
      type: String,
      default: function() {
        return null;
      },
    },
  },
  data: () => ({
    loading: false,
    chartDatas: null,
    pageSize: 10,
    limit: 10,
    canLoadMore: false,
    extensionApp: 'AnalyticsSamples',
    sampleItemExtensionType: 'SampleItem',
    sampleItemExtensions: {},
    // Independent from the chart's own period selector: filtering here must
    // only affect the samples listed in this drawer, never the chart itself.
    localPeriod: null,
    // Plain native date inputs, not the shared select-period widget: that
    // component recomputes its calendar from toLocaleDateString() (locale-
    // dependent, e.g. "01/07/2026") whenever its dropdown reopens with an
    // existing value, which the underlying Vuetify date-picker can't parse
    // (it requires ISO yyyy-MM-dd) — a pre-existing bug, not introduced here.
    fromDate: null,
    toDate: null,
    fromTime: '00:00',
    toTime: '23:59',
    drawerOpened: false,
  }),
  watch: {
    loading() {
      if (this.loading) {
        this.$refs.samplesDrawer.startLoading();
      } else {
        this.$refs.samplesDrawer.endLoading();
        this.computeCanLoadMore();
      }
    },
  },
  created() {
    if (!this.$root.users) {
      this.$root.users = {};
    }
    if (!this.$root.spaces) {
      this.$root.spaces = {};
    }
    document.addEventListener(`extension-${this.extensionApp}-${this.sampleItemExtensionType}-updated`, this.refreshSampleItemExtensions);
    this.refreshSampleItemExtensions();
  },
  beforeDestroy() {
    document.removeEventListener(`extension-${this.extensionApp}-${this.sampleItemExtensionType}-updated`, this.refreshSampleItemExtensions);
  },
  methods: {
    open() {
      this.$refs.samplesDrawer.open();
      // Own copy of the period: changing it must never mutate the parent
      // chart's selectedPeriod prop.
      this.localPeriod = this.selectedPeriod && {...this.selectedPeriod} || null;
      const fromDate = this.localPeriod && new Date(this.localPeriod.min);
      const toDate = this.localPeriod && new Date(this.localPeriod.max);
      this.fromDate = fromDate && this.toIsoDate(fromDate) || null;
      this.toDate = toDate && this.toIsoDate(toDate) || null;
      this.fromTime = fromDate && this.toIsoTime(fromDate) || '00:00';
      this.toTime = toDate && this.toIsoTime(toDate) || '23:59';
      this.drawerOpened = true;
      this.loadData();
    },
    closed() {
      this.drawerOpened = false;
      this.$emit('cancel');
    },
    toIsoDate(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    },
    toIsoTime(date) {
      const pad = n => `${n}`.padStart(2, '0');
      return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    },
    applyDateFilter() {
      if (!this.fromDate || !this.toDate) {
        return;
      }
      this.localPeriod = {
        min: new Date(`${this.fromDate}T${this.fromTime || '00:00'}:00`).getTime(),
        max: new Date(`${this.toDate}T${this.toTime || '23:59'}:59.999`).getTime(),
      };
      this.limit = this.pageSize;
      this.loadData();
    },
    loadMore() {
      this.limit += this.pageSize;
      this.loadData();
    },
    refresh() {
      this.loadData();
    },
    loadData() {
      if (!this.localPeriod) {
        return;
      }
      const params = {
        lang: eXo.env.portal.language && eXo.env.portal.language.replace('_','-'),
        min: this.localPeriod.min,
        max: this.localPeriod.max + 60000,
        timeZone: this.$analyticsUtils.USER_TIMEZONE_ID,
        limit: this.limit,
      };

      this.loading = true;
      return fetch(this.retrieveSamplesUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'pragma': 'no-cache',
          'cache-control': 'no-cache',
        },
        body: $.param(params),
      })
        .then((resp) => {
          if (resp && resp.ok) {
            return resp.json();
          } else {
            throw new Error('Error getting analytics samples with filters:', params);
          }
        })
        .then((chartDatas) => this.chartDatas = chartDatas)
        .catch((e) => {
          console.error('fetch analytics - error', e);
          this.error = 'Error getting analytics';
        })
        .finally(() => this.loading = false);
    },
    computeCanLoadMore() {
      if (this.chartDatas) {
        const loadedDataLength = Object.keys(this.chartDatas).length;
        this.canLoadMore = loadedDataLength >= this.limit;
      } else {
        this.canLoadMore = false;
      }
    },
    refreshSampleItemExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.extensionApp, this.sampleItemExtensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.type && extension.options && (!this.sampleItemExtensions[extension.type] || this.sampleItemExtensions[extension.type] !== extension.options)) {
          this.sampleItemExtensions[extension.type] = extension.options;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension type
      if (changed) {
        this.sampleItemExtensions = Object.assign({}, this.sampleItemExtensions);
      }
    },
  }
};
</script>
